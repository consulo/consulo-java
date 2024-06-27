/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.DebugProcessListener;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaMethodBreakpointProperties;
import com.intellij.java.debugger.impl.engine.ContextUtil;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.TextWithImportsImpl;
import com.intellij.java.debugger.impl.engine.evaluation.expression.Evaluator;
import com.intellij.java.debugger.impl.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.java.debugger.impl.engine.evaluation.expression.ExpressionEvaluatorImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.jdi.DecompiledLocalVariable;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import com.intellij.java.debugger.impl.settings.CapturePoint;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import consulo.application.ApplicationManager;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.application.util.function.ThrowableComputable;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import consulo.internal.com.sun.jdi.*;
import consulo.internal.com.sun.jdi.event.LocatableEvent;
import consulo.java.debugger.impl.JavaRegistry;
import consulo.logging.Logger;
import consulo.util.collection.Maps;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author egor
 */
public class StackCapturingLineBreakpoint extends WildcardMethodBreakpoint {
  private static final Logger LOG = Logger.getInstance(StackCapturingLineBreakpoint.class);

  private final CapturePoint myCapturePoint;
  private final String mySignature;

  private final MyEvaluator myCaptureEvaluator;
  private final MyEvaluator myInsertEvaluator;

  public static final Key<List<StackCapturingLineBreakpoint>> CAPTURE_BREAKPOINTS = Key.create("CAPTURE_BREAKPOINTS");
  private static final Key<Map<Object, List<StackFrameItem>>> CAPTURED_STACKS = Key.create("CAPTURED_STACKS");
  private static final int MAX_STORED_STACKS = 1000;
  public static final int MAX_STACK_LENGTH = 500;

  private final JavaMethodBreakpointProperties myProperties = new JavaMethodBreakpointProperties();

  public StackCapturingLineBreakpoint(Project project, CapturePoint capturePoint) {
    super(project, null);
    myCapturePoint = capturePoint;
    mySignature = null;
    myProperties.EMULATED = true;
    myProperties.WATCH_EXIT = false;
    myProperties.myClassPattern = myCapturePoint.myClassName;
    myProperties.myMethodName = myCapturePoint.myMethodName;

    myCaptureEvaluator = new MyEvaluator(myCapturePoint.myCaptureKeyExpression);
    myInsertEvaluator = new MyEvaluator(myCapturePoint.myInsertKeyExpression);
  }

  @Nonnull
  @Override
  protected JavaMethodBreakpointProperties getProperties() {
    return myProperties;
  }

  @Override
  public String getSuspendPolicy() {
    return DebuggerSettings.SUSPEND_THREAD;
  }

  @Override
  public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
    SuspendContextImpl suspendContext = action.getSuspendContext();
    if (suspendContext != null) {
      ThreadReferenceProxyImpl thread = suspendContext.getThread();
      if (thread != null) {
        DebugProcessImpl process = suspendContext.getDebugProcess();
        try {
          StackFrameProxyImpl frameProxy = ContainerUtil.getFirstItem(thread.forceFrames());
          if (frameProxy != null) {
            Map<Object, List<StackFrameItem>> stacks = process.getUserData(CAPTURED_STACKS);
            if (stacks == null) {
              stacks = new CapturedStacksMap();
              putProcessUserData(CAPTURED_STACKS, Collections.synchronizedMap(stacks), process);
            }
            Value key = myCaptureEvaluator.evaluate(new EvaluationContextImpl(suspendContext, frameProxy));
            if (key instanceof ObjectReference) {
              List<StackFrameItem> frames = StackFrameItem.createFrames(suspendContext, true);
              if (frames.size() > MAX_STACK_LENGTH) {
                frames = frames.subList(0, MAX_STACK_LENGTH);
              }
              stacks.put(getKey((ObjectReference) key), frames);
            }
          }
        } catch (EvaluateException e) {
          LOG.debug(e);
          process.printToConsole(DebuggerBundle.message("error.unable.to.evaluate.capture.expression", e.getMessage()) + "\n");
        }
      }
    }

    return false;
  }

  @Override
  protected void fireBreakpointChanged() {
  }

  private static class CapturedStacksMap extends LinkedHashMap<Object, List<StackFrameItem>> {
    @Override
    protected boolean removeEldestEntry(Map.Entry eldest) {
      return size() > MAX_STORED_STACKS;
    }
  }

  @Override
  public List<Method> matchingMethods(List<Method> methods, DebugProcessImpl debugProcess) {
    String methodName = getMethodName();
    return methods.stream().filter(m -> Comparing.equal(methodName, m.name()) && (mySignature == null || Comparing.equal(mySignature, m.signature()))).limit(1).collect(Collectors.toList());
  }

  public static void recreateAll(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    List<StackCapturingLineBreakpoint> bpts = debugProcess.getUserData(CAPTURE_BREAKPOINTS);
    if (!ContainerUtil.isEmpty(bpts)) {
      bpts.forEach(debugProcess.getRequestsManager()::deleteRequest);
      bpts.clear();
    }
    if (JavaRegistry.DEBUGGER_CAPTURE_POINTS) {
      DebuggerSettings.getInstance().getCapturePoints().stream().filter(c -> c.myEnabled).forEach(c -> track(debugProcess, c));
    }
  }

  public static void clearCaches(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    List<StackCapturingLineBreakpoint> bpts = debugProcess.getUserData(CAPTURE_BREAKPOINTS);
    if (!ContainerUtil.isEmpty(bpts)) {
      bpts.forEach(b -> {
        b.myCaptureEvaluator.clearCache();
        b.myInsertEvaluator.clearCache();
      });
    }
  }

  @Override
  public void createRequest(DebugProcessImpl debugProcess) {
    if (!StringUtil.isEmpty(getClassName())) {
      super.createRequest(debugProcess);
    }
  }

  private static void track(DebugProcessImpl debugProcess, CapturePoint capturePoint) {
    StackCapturingLineBreakpoint breakpoint = new StackCapturingLineBreakpoint(debugProcess.getProject(), capturePoint);
    breakpoint.createRequest(debugProcess);
    List<StackCapturingLineBreakpoint> bpts = debugProcess.getUserData(CAPTURE_BREAKPOINTS);
    if (bpts == null) {
      bpts = new CopyOnWriteArrayList<>();
      putProcessUserData(CAPTURE_BREAKPOINTS, bpts, debugProcess);
    }
    bpts.add(breakpoint);
  }

  public static <T> void putProcessUserData(@Nonnull Key<T> key, @Nullable T value, DebugProcessImpl debugProcess) {
    debugProcess.putUserData(key, value);
    debugProcess.addDebugProcessListener(new DebugProcessListener() {
      @Override
      public void processDetached(DebugProcess process, boolean closedByUser) {
        process.putUserData(key, null);
      }
    });
  }

  @Nullable
  public static CapturePoint getMatchingDisabledInsertionPoint(@Nonnull StackFrameProxyImpl frame) {
    try {
      Location location = frame.location();
      String className = location.declaringType().name();
      String methodName = location.method().name();

      for (CapturePoint c : DebuggerSettings.getInstance().getCapturePoints()) {
        if (!c.myEnabled && StringUtil.equals(c.myInsertClassName, className) && StringUtil.equals(c.myInsertMethodName, methodName)) {
          return c;
        }
      }
    } catch (EvaluateException e) {
      LOG.debug(e);
    }
    return null;
  }

  @Nullable
  public static List<StackFrameItem> getRelatedStack(@Nonnull StackFrameProxyImpl frame, @Nonnull SuspendContextImpl suspendContext) {
    DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
    Map<Object, List<StackFrameItem>> capturedStacks = debugProcess.getUserData(CAPTURED_STACKS);
    if (ContainerUtil.isEmpty(capturedStacks)) {
      return null;
    }
    List<StackCapturingLineBreakpoint> captureBreakpoints = debugProcess.getUserData(CAPTURE_BREAKPOINTS);
    if (ContainerUtil.isEmpty(captureBreakpoints)) {
      return null;
    }
    try {
      Location location = frame.location();
      String className = location.declaringType().name();
      String methodName = location.method().name();

      for (StackCapturingLineBreakpoint b : captureBreakpoints) {
        String insertClassName = b.myCapturePoint.myInsertClassName;
        if ((StringUtil.isEmpty(insertClassName) || StringUtil.equals(insertClassName, className)) && StringUtil.equals(b.myCapturePoint.myInsertMethodName, methodName)) {
          try {
            Value key = b.myInsertEvaluator.evaluate(new EvaluationContextImpl(suspendContext, frame));
            if (key instanceof ObjectReference) {
              return capturedStacks.get(getKey((ObjectReference) key));
            }
          } catch (EvaluateException e) {
            LOG.debug(e);
            debugProcess.printToConsole(DebuggerBundle.message("error.unable.to.evaluate.insert.expression", e.getMessage()) + "\n");
          }
        }
      }
    } catch (EvaluateException e) {
      LOG.debug(e);
    }
    return null;
  }

  @Nullable
  public static List<StackFrameItem> getRelatedStack(@Nullable ObjectReference key, @Nullable DebugProcessImpl process) {
    if (process != null && key != null) {
      Map<Object, List<StackFrameItem>> data = process.getUserData(CAPTURED_STACKS);
      if (data != null) {
        return data.get(key);
      }
    }
    return null;
  }

  private static Object getKey(ObjectReference reference) {
    return reference instanceof StringReference ? ((StringReference) reference).value() : reference;
  }

  private static class MyEvaluator {
    private final String myExpression;
    private ExpressionEvaluator myEvaluator;
    private final Map<Location, ExpressionEvaluator> myEvaluatorCache = Maps.newWeakHashMap();

    public MyEvaluator(String expression) {
      myExpression = expression;
      int paramId = DecompiledLocalVariable.getParamId(myExpression);
      boolean paramEvaluator = paramId > -1;
      if (paramEvaluator) {
        myEvaluator = new ExpressionEvaluatorImpl(new Evaluator() {
          @Override
          public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
            @SuppressWarnings("ConstantConditions") List<Value> argumentValues = context.getFrameProxy().getArgumentValues();
            if (paramId >= argumentValues.size()) {
              throw new EvaluateException("Param index " + paramId + " requested, but only " + argumentValues.size() + " available");
            }
            return argumentValues.get(paramId);
          }
        });
      }
    }

    @Nullable
    Value evaluate(final EvaluationContext context) throws EvaluateException {
      ExpressionEvaluator evaluator = myEvaluator;
      if (evaluator == null) {
        @SuppressWarnings("ConstantConditions") Location location = context.getFrameProxy().location();
        evaluator = myEvaluatorCache.get(location);
        if (evaluator == null && !StringUtil.isEmpty(myExpression)) {
          evaluator = ApplicationManager.getApplication().runReadAction((ThrowableComputable<ExpressionEvaluator, EvaluateException>) () -> {
            SourcePosition sourcePosition = ContextUtil.getSourcePosition(context);
            PsiElement contextElement = ContextUtil.getContextElement(sourcePosition);
            return EvaluatorBuilderImpl.build(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, myExpression), contextElement, sourcePosition, context.getProject());
          });
          myEvaluatorCache.put(location, evaluator);
        }
      }
      if (evaluator != null) {
        return evaluator.evaluate(context);
      }
      return null;
    }

    void clearCache() {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      myEvaluatorCache.clear();
    }
  }
}
