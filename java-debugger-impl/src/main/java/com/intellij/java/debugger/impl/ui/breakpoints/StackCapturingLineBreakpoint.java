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

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.AsyncStacksUtils;
import com.intellij.java.debugger.impl.engine.ContextUtil;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.SyntheticMethodBreakpoint;
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
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.application.ReadAction;
import consulo.application.util.registry.Registry;
import consulo.internal.com.sun.jdi.IncompatibleThreadStateException;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.StringReference;
import consulo.internal.com.sun.jdi.Value;
import consulo.internal.com.sun.jdi.event.LocatableEvent;
import consulo.language.psi.PsiElement;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Lists;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author egor
 */
public class StackCapturingLineBreakpoint extends SyntheticMethodBreakpoint {
    private static final Logger LOG = Logger.getInstance(StackCapturingLineBreakpoint.class);

    private final CapturePoint myCapturePoint;

    private final MyEvaluator myCaptureEvaluator;
    private final MyEvaluator myInsertEvaluator;

    public static final Key<List<StackCapturingLineBreakpoint>> CAPTURE_BREAKPOINTS = Key.create("CAPTURE_BREAKPOINTS");
    private static final Key<Map<Object, List<StackFrameItem>>> CAPTURED_STACKS = Key.create("CAPTURED_STACKS");
    private static final int MAX_STORED_STACKS = 1000;

    public StackCapturingLineBreakpoint(Project project, CapturePoint capturePoint) {
        super(capturePoint.myClassName, capturePoint.myMethodName, null, project);
        myCapturePoint = capturePoint;
        myCaptureEvaluator = new MyEvaluator(myCapturePoint.myCaptureKeyExpression);
        myInsertEvaluator = new MyEvaluator(myCapturePoint.myInsertKeyExpression);
        setSuspendPolicy(DebuggerSettings.SUSPEND_THREAD);
    }

    @Override
    public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) {
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
                            stacks = new FixedHashMap<>(MAX_STORED_STACKS);
                            AsyncStacksUtils.putProcessUserData(CAPTURED_STACKS, Collections.synchronizedMap(stacks), process);
                        }
                        Value key = myCaptureEvaluator.evaluate(new EvaluationContextImpl(suspendContext, frameProxy));
                        if (key instanceof ObjectReference) {
                            List<StackFrameItem> frames = StackFrameItem.createFrames(suspendContext, true);
                            frames = Lists.getFirstItems(frames, AsyncStacksUtils.getMaxStackLength());
                            stacks.put(getKey((ObjectReference)key), frames);
                        }
                    }
                }
                catch (EvaluateException e) {
                    LOG.debug(e);
                    process.printToConsole(JavaDebuggerLocalize.errorUnableToEvaluateCaptureExpression(e.getMessage()).get() + "\n");
                }
            }
        }

        return false;
    }

    public static void deleteAll(DebugProcessImpl debugProcess) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        List<StackCapturingLineBreakpoint> bpts = debugProcess.getUserData(CAPTURE_BREAKPOINTS);
        if (!ContainerUtil.isEmpty(bpts)) {
            bpts.forEach(debugProcess.getRequestsManager()::deleteRequest);
            bpts.clear();
        }
    }

    public static void createAll(DebugProcessImpl debugProcess) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        if (Registry.is("debugger.async.stacks.via.breakpoints", false)) {
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

    @Override
    public boolean isEnabled() {
        return myCapturePoint.myEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        myCapturePoint.myEnabled = enabled;
        DebuggerSettings.getInstance().setCapturePoints(DebuggerSettings.getInstance().getCapturePoints()); // to fire change event
    }

    private static void track(DebugProcessImpl debugProcess, CapturePoint capturePoint) {
        StackCapturingLineBreakpoint breakpoint = new StackCapturingLineBreakpoint(debugProcess.getProject(), capturePoint);
        breakpoint.createRequest(debugProcess);
        List<StackCapturingLineBreakpoint> bpts = debugProcess.getUserData(CAPTURE_BREAKPOINTS);
        if (bpts == null) {
            bpts = new CopyOnWriteArrayList<>();
            AsyncStacksUtils.putProcessUserData(CAPTURE_BREAKPOINTS, bpts, debugProcess);
        }
        bpts.add(breakpoint);
    }

    public static @Nullable List<StackFrameItem> getRelatedStack(StackFrameProxyImpl frame, SuspendContextImpl suspendContext) {
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
            String methodName = DebuggerUtilsEx.getLocationMethodName(location);

            for (StackCapturingLineBreakpoint b : captureBreakpoints) {
                String insertClassName = b.myCapturePoint.myInsertClassName;
                if ((StringUtil.isEmpty(insertClassName) || StringUtil.equals(insertClassName, className)) &&
                    StringUtil.equals(b.myCapturePoint.myInsertMethodName, methodName)) {
                    try {
                        Value key = b.myInsertEvaluator.evaluate(new EvaluationContextImpl(suspendContext, frame));
                        return key instanceof ObjectReference ? capturedStacks.get(getKey((ObjectReference)key)) : null;
                    }
                    catch (EvaluateException e) {
                        LOG.debug(e);
                        if (!(e.getCause() instanceof IncompatibleThreadStateException)) {
                            debugProcess.printToConsole(
                                JavaDebuggerLocalize.errorUnableToEvaluateInsertExpression(e.getMessage()).get() + "\n");
                        }
                    }
                }
            }
        }
        catch (EvaluateException e) {
            LOG.debug(e);
        }
        return null;
    }

    public static @Nullable List<StackFrameItem> getRelatedStack(@Nullable ObjectReference key, @Nullable DebugProcessImpl process) {
        if (process != null && key != null) {
            Map<Object, List<StackFrameItem>> data = process.getUserData(CAPTURED_STACKS);
            if (data != null) {
                return data.get(getKey(key));
            }
        }
        return null;
    }

    private static Object getKey(ObjectReference reference) {
        return reference instanceof StringReference ? ((StringReference)reference).value() : reference;
    }

    /**
     * {@link Map} which stores not more than {@code maxSize} entries.
     * On attempt to put more, the eldest element is removed.
     * <p>
     * Port of JetBrains {@code com.intellij.util.containers.FixedHashMap}, which has no Consulo counterpart.
     */
    private static final class FixedHashMap<K, V> extends LinkedHashMap<K, V> {
        private final int myMaxSize;

        FixedHashMap(int maxSize) {
            myMaxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > myMaxSize;
        }
    }

    private static class MyEvaluator {
        private final String myExpression;
        private @Nullable ExpressionEvaluator myEvaluator;
        private final Map<Location, ExpressionEvaluator> myEvaluatorCache = new WeakHashMap<>();

        MyEvaluator(String expression) {
            myExpression = expression;
            int paramId = DecompiledLocalVariable.getParamId(myExpression);
            boolean paramEvaluator = paramId > -1;
            if (paramEvaluator) {
                myEvaluator = new ExpressionEvaluatorImpl(new Evaluator() {
                    @Override
                    public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
                        @SuppressWarnings("ConstantConditions")
                        List<Value> argumentValues = context.getFrameProxy().getArgumentValues();
                        if (paramId >= argumentValues.size()) {
                            throw new EvaluateException(
                                "Param index " + paramId + " requested, but only " + argumentValues.size() + " available");
                        }
                        return argumentValues.get(paramId);
                    }
                });
            }
        }

        @Nullable
        Value evaluate(EvaluationContext context) throws EvaluateException {
            ExpressionEvaluator evaluator = myEvaluator;
            if (evaluator == null) {
                @SuppressWarnings("ConstantConditions")
                Location location = context.getFrameProxy().location();
                evaluator = location == null ? null : myEvaluatorCache.get(location);
                if (evaluator == null && !StringUtil.isEmpty(myExpression)) {
                    evaluator = ReadAction.compute(() -> {
                        SourcePosition sourcePosition = ContextUtil.getSourcePosition(context);
                        PsiElement contextElement = ContextUtil.getContextElement(sourcePosition);
                        return EvaluatorBuilderImpl.build(
                            new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, myExpression),
                            contextElement,
                            sourcePosition,
                            context.getProject()
                        );
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
