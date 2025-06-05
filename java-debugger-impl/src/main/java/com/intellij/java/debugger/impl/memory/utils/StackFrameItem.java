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
package com.intellij.java.debugger.impl.memory.utils;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.*;
import com.intellij.java.debugger.impl.jdi.*;
import com.intellij.java.debugger.impl.settings.CaptureConfigurable;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.java.debugger.impl.ui.tree.render.ClassRenderer;
import com.intellij.java.language.psi.CommonClassNames;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.*;
import consulo.execution.debug.frame.presentation.XStringValuePresentation;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.execution.debug.ui.XDebuggerUIConstants;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.internal.com.sun.jdi.*;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StackFrameItem {
  private static final Logger LOG = Logger.getInstance(StackFrameItem.class);
  private static final List<XNamedValue> VARS_CAPTURE_DISABLED = Collections.singletonList(JavaStackFrame.createMessageNode(DebuggerBundle.message("message.node.local.variables.capture.disabled"),
      null));
  private static final List<XNamedValue> VARS_NOT_CAPTURED = Collections.singletonList(JavaStackFrame.createMessageNode(DebuggerBundle.message("message.node.local.variables.not.captured"),
      XDebuggerUIConstants.INFORMATION_MESSAGE_ICON));

  public static final XDebuggerTreeNodeHyperlink CAPTURE_SETTINGS_OPENER = new XDebuggerTreeNodeHyperlink(" settings") {
    @Override
    public void onClick(MouseEvent event) {
      ShowSettingsUtil.getInstance().showSettingsDialog(null, CaptureConfigurable.class);
      event.consume();
    }
  };

  private final Location myLocation;
  private final List<XNamedValue> myVariables;

  public StackFrameItem(Location location, List<XNamedValue> variables) {
    myLocation = location;
    myVariables = variables;
  }

  @Nonnull
  public String path() {
    return myLocation.declaringType().name();
  }

  public int line() {
    return DebuggerUtilsEx.getLineNumber(myLocation, false);
  }

  @Nonnull
  public static List<StackFrameItem> createFrames(@Nonnull SuspendContextImpl suspendContext, boolean withVars) throws EvaluateException {
    ThreadReferenceProxyImpl threadReferenceProxy = suspendContext.getThread();
    if (threadReferenceProxy != null) {
      List<StackFrameProxyImpl> frameProxies = threadReferenceProxy.forceFrames();
      List<StackFrameItem> res = new ArrayList<>(frameProxies.size());
      for (StackFrameProxyImpl frame : frameProxies) {
        try {
          List<XNamedValue> vars = null;
          Location location = frame.location();
          Method method = location.method();
          if (withVars) {
            if (!DebuggerSettings.getInstance().CAPTURE_VARIABLES) {
              vars = VARS_CAPTURE_DISABLED;
            } else if (method.isNative() || method.isBridge() || DebuggerUtils.isSynthetic(method)) {
              vars = VARS_NOT_CAPTURED;
            } else {
              vars = new ArrayList<>();

              try {
                ObjectReference thisObject = frame.thisObject();
                if (thisObject != null) {
                  vars.add(createVariable(thisObject, "this", VariableItem.VarType.OBJECT));
                }
              } catch (EvaluateException e) {
                LOG.debug(e);
              }

              try {
                for (LocalVariableProxyImpl v : frame.visibleVariables()) {
                  try {
                    VariableItem.VarType varType = v.getVariable().isArgument() ? VariableItem.VarType.PARAM : VariableItem.VarType.OBJECT;
                    vars.add(createVariable(frame.getValue(v), v.name(), varType));
                  } catch (EvaluateException e) {
                    LOG.debug(e);
                  }
                }
              } catch (EvaluateException e) {
                if (e.getCause() instanceof AbsentInformationException) {
                  vars.add(JavaStackFrame.LOCAL_VARIABLES_INFO_UNAVAILABLE_MESSAGE_NODE);
                  // only args for frames w/o debug info for now
                  try {
                    for (Map.Entry<DecompiledLocalVariable, Value> entry : LocalVariablesUtil.fetchValues(frame, suspendContext.getDebugProcess(), false).entrySet()) {
                      vars.add(createVariable(entry.getValue(), entry.getKey().getDisplayName(), VariableItem.VarType.PARAM));
                    }
                  } catch (Exception ex) {
                    LOG.info(ex);
                  }
                } else {
                  LOG.debug(e);
                }
              }
            }
          }

          StackFrameItem frameItem = new StackFrameItem(location, vars);
          res.add(frameItem);

          List<StackFrameItem> relatedStack = StackCapturingLineBreakpoint.getRelatedStack(frame, suspendContext);
          if (!ContainerUtil.isEmpty(relatedStack)) {
            res.add(null); // separator
            res.addAll(relatedStack);
            break;
          }
        } catch (EvaluateException e) {
          LOG.debug(e);
        }
      }
      return res;
    }
    return Collections.emptyList();
  }

  private static VariableItem createVariable(Value value, String name, VariableItem.VarType varType) {
    String type = null;
    String valueText = "null";
    if (value instanceof ObjectReference) {
      valueText = value instanceof StringReference ? ((StringReference) value).value() : "";
      type = value.type().name() + "@" + ((ObjectReference) value).uniqueID();
    } else if (value != null) {
      valueText = value.toString();
    }
    return new VariableItem(name, type, valueText, varType);
  }

  @Override
  public String toString() {
    return myLocation.toString();
  }

  private static class VariableItem extends XNamedValue {
    enum VarType {
      PARAM,
      OBJECT
    }

    private final String myType;
    private final String myValue;
    private final VarType myVarType;

    public VariableItem(String name, String type, String value, VarType varType) {
      super(name);
      myType = type;
      myValue = value;
      myVarType = varType;
    }

    @Override
    public void computePresentation(@Nonnull XValueNode node, @Nonnull XValuePlace place) {
      ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
      String type = classRenderer.renderTypeName(myType);
      Image icon = myVarType == VariableItem.VarType.PARAM ? PlatformIconGroup.nodesParameter() : ExecutionDebugIconGroup.nodeValue();
      if (myType != null && myType.startsWith(CommonClassNames.JAVA_LANG_STRING + "@")) {
        node.setPresentation(icon, new XStringValuePresentation(myValue) {
          @Nullable
          @Override
          public String getType() {
            return classRenderer.SHOW_STRINGS_TYPE ? type : null;
          }
        }, false);
        return;
      }
      node.setPresentation(icon, type, myValue, false);
    }
  }

  public CapturedStackFrame createFrame(DebugProcessImpl debugProcess) {
    return new CapturedStackFrame(debugProcess, this);
  }

  public static class CapturedStackFrame extends XStackFrame implements JVMStackFrameInfoProvider, XStackFrameWithSeparatorAbove {
    private final XSourcePosition mySourcePosition;
    private final boolean myIsSynthetic;
    private final boolean myIsInLibraryContent;

    private final String myPath;
    private final String myMethodName;
    private final int myLineNumber;

    private final List<XNamedValue> myVariables;

    private volatile boolean myWithSeparator;

    public CapturedStackFrame(DebugProcessImpl debugProcess, StackFrameItem item) {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      mySourcePosition = DebuggerUtilsEx.toXSourcePosition(debugProcess.getPositionManager().getSourcePosition(item.myLocation));
      myIsSynthetic = DebuggerUtils.isSynthetic(item.myLocation.method());
      myIsInLibraryContent = DebuggerUtilsEx.isInLibraryContent(mySourcePosition != null ? mySourcePosition.getFile() : null, debugProcess.getProject());
      myPath = item.path();
      myMethodName = item.myLocation.method().name();
      myLineNumber = item.line();
      myVariables = item.myVariables;
    }

    @Nullable
    @Override
    public XSourcePosition getSourcePosition() {
      return mySourcePosition;
    }

    public boolean isSynthetic() {
      return myIsSynthetic;
    }

    public boolean isInLibraryContent() {
      return myIsInLibraryContent;
    }

    @Override
    public void customizePresentation(@Nonnull ColoredTextContainer component) {
      component.setIcon(Image.empty(6));
      component.append(String.format("%s:%d, %s", myMethodName, myLineNumber, StringUtil.getShortName(myPath)), getAttributes());
      String packageName = StringUtil.getPackageName(myPath);
      if (!packageName.trim().isEmpty()) {
        component.append(String.format(" (%s)", packageName), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
      }
    }

    @Override
    public void computeChildren(@Nonnull XCompositeNode node) {
      if (myVariables == VARS_CAPTURE_DISABLED) {
        node.setMessage(DebuggerBundle.message("message.node.local.variables.capture.disabled"), null, SimpleTextAttributes.REGULAR_ATTRIBUTES, CAPTURE_SETTINGS_OPENER);
      } else if (myVariables != null) {
        XValueChildrenList children = new XValueChildrenList();
        myVariables.forEach(children::add);
        node.addChildren(children, true);
      } else {
        node.addChildren(XValueChildrenList.EMPTY, true);
      }
    }

    private SimpleTextAttributes getAttributes() {
      if (isSynthetic() || isInLibraryContent()) {
        return SimpleTextAttributes.GRAYED_ATTRIBUTES;
      }
      return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    @Override
    public boolean hasSeparatorAbove() {
      return myWithSeparator;
    }

    public void setWithSeparator(boolean withSeparator) {
      myWithSeparator = withSeparator;
    }

    @Override
    public String toString() {
      if (mySourcePosition != null) {
        return mySourcePosition.getFile().getName() + ":" + (mySourcePosition.getLine() + 1);
      }
      return "<position unknown>";
    }
  }
}
