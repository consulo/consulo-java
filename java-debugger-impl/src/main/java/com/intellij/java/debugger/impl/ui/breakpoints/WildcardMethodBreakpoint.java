/*
 * Copyright 2004-2006 Alexey Efimov
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
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaMethodBreakpointProperties;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.requests.RequestManagerImpl;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.ReadAction;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.internal.com.sun.jdi.AbsentInformationException;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.Method;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.event.LocatableEvent;
import consulo.internal.com.sun.jdi.event.MethodEntryEvent;
import consulo.internal.com.sun.jdi.event.MethodExitEvent;
import consulo.internal.com.sun.jdi.request.MethodEntryRequest;
import consulo.internal.com.sun.jdi.request.MethodExitRequest;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import consulo.util.lang.PatternUtil;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public class WildcardMethodBreakpoint extends Breakpoint<JavaMethodBreakpointProperties> implements MethodBreakpointBase {
    private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.ExceptionBreakpoint");

    public WildcardMethodBreakpoint(Project project, XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
        super(project, breakpoint);
    }

    @Override
    public Key<MethodBreakpoint> getCategory() {
        return MethodBreakpoint.CATEGORY;
    }

    protected WildcardMethodBreakpoint(Project project, @Nonnull String classPattern, @Nonnull String methodName, XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
        super(project, breakpoint);
        setClassPattern(classPattern);
        setMethodName(methodName);
    }

    @Override
    public String getClassName() {
        return getClassPattern();
    }

    @Override
    @Nullable
    public String getShortClassName() {
        return getClassName();
    }

    public String getMethodName() {
        return getProperties().myMethodName;
    }

    @Override
    public void disableEmulation() {
        MethodBreakpointBase.disableEmulation(this);
    }

    @Override
    public PsiClass getPsiClass() {
        return ReadAction.compute(() -> getClassName() != null ? DebuggerUtils.findClass(getClassName(), myProject, GlobalSearchScope.allScope(myProject)) : null);
    }

    @Override
    public String getDisplayName() {
        if (!isValid()) {
            return DebuggerBundle.message("status.breakpoint.invalid");
        }
        final StringBuilder buffer = new StringBuilder();
        buffer.append(getClassPattern());
        buffer.append(".");
        buffer.append(getMethodName());
        buffer.append("()");
        return buffer.toString();
    }

    @Override
    public Image getIcon() {
        if (!isEnabled()) {
            final Breakpoint master = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this);
            return master == null ? ExecutionDebugIconGroup.breakpointBreakpointmethoddisabled() : ExecutionDebugIconGroup.breakpointBreakpointmethoddependent();
        }
        return ExecutionDebugIconGroup.breakpointBreakpointmethod();
    }

    @Override
    public void reload() {
    }

    @Override
    public boolean evaluateCondition(EvaluationContextImpl context, LocatableEvent event) throws EvaluateException {
        return (isEmulated() || matchesMethod(event.location().method())) && super.evaluateCondition(context, event);
    }

    @Override
    public void createRequest(DebugProcessImpl debugProcess) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        if (!shouldCreateRequest(debugProcess)) {
            return;
        }
        if (isEmulated()) {
            createOrWaitPrepare(debugProcess, getClassPattern());
        }
        else {
            try {
                RequestManagerImpl requestManager = debugProcess.getRequestsManager();
                if (isWatchEntry()) {
                    MethodEntryRequest entryRequest = MethodBreakpoint.findRequest(debugProcess, MethodEntryRequest.class, this);
                    if (entryRequest == null) {
                        entryRequest = requestManager.createMethodEntryRequest(this);
                    }
                    else {
                        entryRequest.disable();
                    }
                    entryRequest.addClassFilter(getClassPattern());
                    debugProcess.getRequestsManager().enableRequest(entryRequest);
                }
                if (isWatchExit()) {
                    MethodExitRequest exitRequest = MethodBreakpoint.findRequest(debugProcess, MethodExitRequest.class, this);
                    if (exitRequest == null) {
                        exitRequest = requestManager.createMethodExitRequest(this);
                    }
                    else {
                        exitRequest.disable();
                    }
                    exitRequest.addClassFilter(getClassPattern());
                    debugProcess.getRequestsManager().enableRequest(exitRequest);
                }
            }
            catch (Exception e) {
                LOG.debug(e);
            }
        }
    }

    @Override
    public void processClassPrepare(DebugProcess debugProcess, ReferenceType refType) {
        if (isEmulated()) {
            MethodBreakpoint.createRequestForPreparedClassEmulated(this, (DebugProcessImpl) debugProcess, refType, true);
        }
        else {
            // should be empty - does not make sense for this breakpoint
        }
    }

    @Override
    public String getEventMessage(LocatableEvent event) {
        final Location location = event.location();
        final String locationQName = DebuggerUtilsEx.getLocationMethodQName(location);
        String locationFileName;
        try {
            locationFileName = location.sourceName();
        }
        catch (AbsentInformationException e) {
            locationFileName = "";
        }
        final int locationLine = location.lineNumber();

        if (event instanceof MethodEntryEvent) {
            MethodEntryEvent entryEvent = (MethodEntryEvent) event;
            final Method method = entryEvent.method();
            return DebuggerBundle.message("status.method.entry.breakpoint.reached", method.declaringType().name() + "." + method.name() + "()", locationQName, locationFileName, locationLine);
        }

        if (event instanceof MethodExitEvent) {
            MethodExitEvent exitEvent = (MethodExitEvent) event;
            final Method method = exitEvent.method();
            return DebuggerBundle.message("status.method.exit.breakpoint.reached", method.declaringType().name() + "." + method.name() + "()", locationQName, locationFileName, locationLine);
        }
        return "";
    }

    @Override
    public boolean isValid() {
        return !StringUtil.isEmpty(getClassPattern()) && !StringUtil.isEmpty(getMethodName());
    }

    //@SuppressWarnings({"HardCodedStringLiteral"}) public void writeExternal(Element parentNode) throws WriteExternalException {
    //  super.writeExternal(parentNode);
    //  parentNode.setAttribute(JDOM_LABEL, "true");
    //  if (getClassPattern() != null) {
    //    parentNode.setAttribute("class_name", getClassPattern());
    //  }
    //  if (getMethodName() != null) {
    //    parentNode.setAttribute("method_name", getMethodName());
    //  }
    //}

    @Override
    public PsiElement getEvaluationElement() {
        return null;
    }

    @Override
    public void readExternal(Element parentNode) throws InvalidDataException {
        super.readExternal(parentNode);

        //noinspection HardCodedStringLiteral
        String className = parentNode.getAttributeValue("class_name");
        setClassPattern(className);

        //noinspection HardCodedStringLiteral
        String methodName = parentNode.getAttributeValue("method_name");
        setMethodName(methodName);

        try {
            getProperties().WATCH_ENTRY = Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "WATCH_ENTRY"));
        }
        catch (Exception ignored) {
        }
        try {
            getProperties().WATCH_EXIT = Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "WATCH_EXIT"));
        }
        catch (Exception ignored) {
        }

        if (className == null || methodName == null) {
            throw new InvalidDataException();
        }
    }

    @Override
    public List<Method> matchingMethods(List<Method> methods, DebugProcessImpl debugProcess) {
        return methods.stream().filter(this::matchesMethod).collect(Collectors.toList());
    }

    private boolean matchesMethod(Method method) {
        StringBuilder sb = new StringBuilder();
        for (String mask : StringUtil.split(getMethodName(), ",")) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append('(').append(PatternUtil.convertToRegex(mask)).append(')');
        }

        try {
            return method != null && Pattern.compile(sb.toString()).matcher(method.name()).matches();
        }
        catch (PatternSyntaxException e) {
            LOG.warn(e);
            return false;
        }
    }

    public static WildcardMethodBreakpoint create(Project project, final String classPattern, final String methodName, XBreakpoint<JavaMethodBreakpointProperties> xBreakpoint) {
        return new WildcardMethodBreakpoint(project, classPattern, methodName, xBreakpoint);
    }

    public boolean isEmulated() {
        return getProperties().EMULATED;
    }

    @Override
    public boolean isWatchEntry() {
        return getProperties().WATCH_ENTRY;
    }

    @Override
    public boolean isWatchExit() {
        return getProperties().WATCH_EXIT;
    }

    private String getClassPattern() {
        return getProperties().myClassPattern;
    }

    private void setClassPattern(String classPattern) {
        getProperties().myClassPattern = classPattern;
    }

    private void setMethodName(String methodName) {
        getProperties().myMethodName = methodName;
    }
}
