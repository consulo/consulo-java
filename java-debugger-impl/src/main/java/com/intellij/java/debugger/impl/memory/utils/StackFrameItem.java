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

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.actions.ThreadDumpAction;
import com.intellij.java.debugger.impl.engine.AsyncStacksUtils;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.JVMStackFrameInfoProvider;
import com.intellij.java.debugger.impl.engine.JavaStackFrame;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.jdi.DecompiledLocalVariable;
import com.intellij.java.debugger.impl.jdi.LocalVariableProxyImpl;
import com.intellij.java.debugger.impl.jdi.LocalVariablesUtil;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import com.intellij.java.debugger.impl.settings.CaptureConfigurable;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.impl.settings.ThreadsViewSettings;
import com.intellij.java.debugger.impl.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.java.debugger.impl.ui.tree.render.ClassRenderer;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.language.psi.CommonClassNames;
import consulo.dataContext.DataManager;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.frame.XCompositeNode;
import consulo.execution.debug.frame.XDebuggerTreeNodeHyperlink;
import consulo.execution.debug.frame.XNamedValue;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.frame.XStackFrameWithSeparatorAbove;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.execution.debug.frame.XValueNode;
import consulo.execution.debug.frame.XValuePlace;
import consulo.execution.debug.frame.presentation.XStringValuePresentation;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.execution.debug.ui.XDebuggerUIConstants;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.internal.com.sun.jdi.AbsentInformationException;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.Method;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.StringReference;
import consulo.internal.com.sun.jdi.Value;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StackFrameItem {
    private static final Logger LOG = Logger.getInstance(StackFrameItem.class);
    private static final List<XNamedValue> VARS_CAPTURE_DISABLED = Collections.singletonList(
        JavaStackFrame.createMessageNode(JavaDebuggerLocalize.messageNodeLocalVariablesCaptureDisabled(), null));
    private static final List<XNamedValue> VARS_NOT_CAPTURED = Collections.singletonList(
        JavaStackFrame.createMessageNode(JavaDebuggerLocalize.messageNodeLocalVariablesNotCaptured(),
                                         XDebuggerUIConstants.INFORMATION_MESSAGE_ICON));

    public static final XDebuggerTreeNodeHyperlink CAPTURE_SETTINGS_OPENER = new XDebuggerTreeNodeHyperlink(
        JavaDebuggerLocalize.captureNodeSettingsLink()) {
        @Override
        public void onClick(MouseEvent event) {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                DataManager.getInstance().getDataContext(event.getComponent()).getData(Project.KEY),
                CaptureConfigurable.class);
            event.consume();
        }
    };

    private final Location myLocation;
    private final @Nullable List<XNamedValue> myVariables;

    public StackFrameItem(Location location, @Nullable List<XNamedValue> variables) {
        myLocation = location;
        myVariables = variables;
    }

    public Location location() {
        return myLocation;
    }

    public String path() {
        return myLocation.declaringType().name();
    }

    public String method() {
        return DebuggerUtilsEx.getLocationMethodName(myLocation);
    }

    public int line() {
        return DebuggerUtilsEx.getLineNumber(myLocation, false);
    }

    public static List<StackFrameItem> createFrames(SuspendContextImpl suspendContext, boolean withVars) throws EvaluateException {
        ThreadReferenceProxyImpl threadReferenceProxy = suspendContext.getThread();
        if (threadReferenceProxy != null) {
            List<StackFrameProxyImpl> frameProxies = threadReferenceProxy.forceFrames();
            List<StackFrameItem> res = new ArrayList<>(frameProxies.size());
            for (StackFrameProxyImpl frame : frameProxies) {
                try {
                    List<XNamedValue> vars;
                    Location location = frame.location();
                    if (withVars) {
                        if (!DebuggerSettings.getInstance().CAPTURE_VARIABLES) {
                            vars = VARS_CAPTURE_DISABLED;
                        }
                        else {
                            Method method = location.method();
                            if (method.isNative() || method.isBridge() || DebuggerUtils.isSynthetic(method)) {
                                vars = VARS_NOT_CAPTURED;
                            }
                            else {
                                vars = new ArrayList<>();

                                try {
                                    ObjectReference thisObject = frame.thisObject();
                                    if (thisObject != null) {
                                        vars.add(createVariable(thisObject, "this", VariableItem.VarType.OBJECT));
                                    }
                                }
                                catch (EvaluateException e) {
                                    LOG.debug(e);
                                }

                                try {
                                    for (LocalVariableProxyImpl v : frame.visibleVariables()) {
                                        try {
                                            VariableItem.VarType varType =
                                                v.getVariable().isArgument() ? VariableItem.VarType.PARAM : VariableItem.VarType.OBJECT;
                                            vars.add(createVariable(frame.getValue(v), v.name(), varType));
                                        }
                                        catch (EvaluateException e) {
                                            LOG.debug(e);
                                        }
                                    }
                                }
                                catch (EvaluateException e) {
                                    if (e.getCause() instanceof AbsentInformationException) {
                                        vars.add(JavaStackFrame.LOCAL_VARIABLES_INFO_UNAVAILABLE_MESSAGE_NODE);
                                        // only args for frames w/o debug info for now
                                        try {
                                            for (Map.Entry<DecompiledLocalVariable, Value> entry : LocalVariablesUtil
                                                .fetchValues(frame, suspendContext.getDebugProcess(), false).entrySet()) {
                                                vars.add(createVariable(entry.getValue(), entry.getKey().getDisplayName(),
                                                                        VariableItem.VarType.PARAM));
                                            }
                                        }
                                        catch (Exception ex) {
                                            LOG.info(ex);
                                        }
                                    }
                                    else {
                                        LOG.debug(e);
                                    }
                                }
                            }
                        }
                    }
                    else {
                        vars = null;
                    }

                    StackFrameItem frameItem = new StackFrameItem(location, vars);
                    res.add(frameItem);

                    List<StackFrameItem> relatedStack = StackCapturingLineBreakpoint.getRelatedStack(frame, suspendContext);
                    if (!ContainerUtil.isEmpty(relatedStack)) {
                        res.add(null); // separator
                        res.addAll(relatedStack);
                        break;
                    }
                }
                catch (EvaluateException e) {
                    LOG.debug(e);
                }
            }
            return res;
        }
        return Collections.emptyList();
    }

    private static VariableItem createVariable(@Nullable Value value, String name, VariableItem.VarType varType) {
        String type = null;
        String valueText = "null";
        if (value instanceof ObjectReference) {
            valueText = value instanceof StringReference ? ((StringReference)value).value() : "";
            type = value.type().name() + "@" + ((ObjectReference)value).uniqueID();
        }
        else if (value != null) {
            valueText = value.toString();
        }
        return new VariableItem(name, type, valueText, varType);
    }

    @Override
    public String toString() {
        return myLocation.toString();
    }

    private static class VariableItem extends XNamedValue {
        enum VarType {PARAM, OBJECT}

        private final @Nullable String myType;
        private final String myValue;
        private final VarType myVarType;

        VariableItem(String name, @Nullable String type, String value, VarType varType) {
            super(name);
            myType = type;
            myValue = value;
            myVarType = varType;
        }

        @Override
        public void computePresentation(XValueNode node, XValuePlace place) {
            ClassRenderer classRenderer = NodeRendererSettings.getInstance().getClassRenderer();
            // Consulo's DebuggerSettings has no SHOW_TYPES, types are always rendered; Consulo's renderTypeName does not accept null
            String type = myType != null ? classRenderer.renderTypeName(myType) : null;
            Image icon = myVarType == VariableItem.VarType.PARAM ? PlatformIconGroup.nodesParameter()
                                                                  : ExecutionDebugIconGroup.nodeValue();
            if (myType != null && myType.startsWith(CommonClassNames.JAVA_LANG_STRING + "@")) {
                node.setPresentation(icon, new XStringValuePresentation(myValue) {
                    @Override
                    public @Nullable String getType() {
                        return classRenderer.SHOW_STRINGS_TYPE ? type : null;
                    }
                }, false);
                return;
            }
            node.setPresentation(icon, type, myValue, false);
        }
    }

    /**
     * @deprecated Use {@link #createFrame(DebugProcessImpl, SourcePosition)} instead
     */
    @Deprecated
    public XStackFrame createFrame(DebugProcessImpl debugProcess) {
        return createFrame(debugProcess, debugProcess.getPositionManager().getSourcePosition(myLocation));
    }

    public XStackFrame createFrame(DebugProcessImpl debugProcess, @Nullable SourcePosition sourcePosition) {
        return new CapturedStackFrame(debugProcess, this, sourcePosition);
    }

    public static boolean hasSeparatorAbove(XStackFrame frame) {
        return frame instanceof XStackFrameWithSeparatorAbove frameWithSeparator &&
               frameWithSeparator.hasSeparatorAbove();
    }

    public static void setWithSeparator(XStackFrame frame) {
        // TODO should call XStackFrameWithSeparatorAbove.setWithSeparator(true) once the platform provides it
        if (frame instanceof StackFrameItem.CapturedStackFrame capturedStackFrame) {
            capturedStackFrame.setWithSeparator(true);
        }
        else if (frame instanceof AsyncStacksUtils.ThrottledFrame throttledFrame) {
            throttledFrame.setWithSeparator(true);
        }
    }

    public static LocalizeValue getAsyncStacktraceMessage() {
        return JavaDebuggerLocalize.framePanelAsyncStacktrace();
    }

    public static class CapturedStackFrame extends XStackFrame implements JVMStackFrameInfoProvider,
                                                                          XStackFrameWithSeparatorAbove {
        private final @Nullable XSourcePosition mySourcePosition;
        private final boolean myIsSynthetic;
        private final boolean myIsInLibraryContent;
        private final boolean myShouldHide;

        private final String myPath;
        private final String myMethodName;
        private final int myLineNumber;
        private final Location myLocation;

        private final @Nullable List<XNamedValue> myVariables;

        private volatile boolean myWithSeparator;

        public CapturedStackFrame(DebugProcessImpl debugProcess, StackFrameItem item, @Nullable SourcePosition sourcePosition) {
            DebuggerManagerThreadImpl.assertIsManagerThread();
            myPath = item.path();
            myMethodName = item.method();
            myLineNumber = item.line();
            myVariables = item.myVariables;

            myLocation = item.myLocation;
            mySourcePosition = DebuggerUtilsEx.toXSourcePosition(sourcePosition);
            myIsSynthetic = DebuggerUtils.isSynthetic(myLocation.method());
            myIsInLibraryContent =
                DebuggerUtilsEx.isInLibraryContent(mySourcePosition != null ? mySourcePosition.getFile() : null, debugProcess.getProject());

            myShouldHide = myIsSynthetic || myIsInLibraryContent;
        }

        @Override
        public @Nullable XSourcePosition getSourcePosition() {
            return mySourcePosition;
        }

        @Override
        public boolean isSynthetic() {
            return myIsSynthetic;
        }

        @Override
        public boolean isInLibraryContent() {
            return myIsInLibraryContent;
        }

        @Override
        public boolean shouldHide() {
            return myShouldHide;
        }

        @Override
        public void customizePresentation(ColoredTextContainer component) {
            doCustomizePresentation(component);
        }

        public void customizeTextPresentation(ColoredTextContainer component) {
            //noinspection HardCodedStringLiteral
            component.append(ThreadDumpAction.renderLocation(myLocation), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }

        private void doCustomizePresentation(ColoredTextContainer component) {
            ThreadsViewSettings settings = ThreadsViewSettings.getInstance();

            component.setIcon(Image.empty(16));
            component.append(myMethodName, getAttributes());
            if (settings.SHOW_LINE_NUMBER) {
                component.append(":" + myLineNumber, getAttributes());
            }
            if (settings.SHOW_CLASS_NAME) {
                component.append(", " + StringUtil.getShortName(myPath), getAttributes());
                String packageName = StringUtil.getPackageName(myPath);
                if (settings.SHOW_PACKAGE_NAME && !packageName.trim().isEmpty()) {
                    component.append(" (" + packageName + ")", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
                }
            }
        }

        @Override
        public void computeChildren(XCompositeNode node) {
            XValueChildrenList children = XValueChildrenList.EMPTY;
            if (myVariables == VARS_CAPTURE_DISABLED) {
                node.setMessage(JavaDebuggerLocalize.messageNodeLocalVariablesCaptureDisabled(), null,
                                SimpleTextAttributes.REGULAR_ATTRIBUTES, CAPTURE_SETTINGS_OPENER);
            }
            else if (myVariables != null) {
                children = new XValueChildrenList(myVariables.size());
                myVariables.forEach(children::add);
            }
            else {
                node.setMessage(JavaDebuggerLocalize.debuggerVariablesNotAvailableInAsync(), PlatformIconGroup.generalInformation(),
                                SimpleTextAttributes.REGULAR_ATTRIBUTES, null);
            }
            node.addChildren(children, true);
        }

        private SimpleTextAttributes getAttributes() {
            if (shouldHide()) {
                return SimpleTextAttributes.GRAYED_ATTRIBUTES;
            }
            return SimpleTextAttributes.REGULAR_ATTRIBUTES;
        }

        public String getCaptionAboveOf() {
            return getAsyncStacktraceMessage().get();
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
