/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Class ExceptionBreakpoint
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.breakpoints.properties.JavaExceptionBreakpointProperties;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.DebuggerManagerThreadImpl;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.internal.com.sun.jdi.AbsentInformationException;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.internal.com.sun.jdi.event.ExceptionEvent;
import consulo.internal.com.sun.jdi.event.LocatableEvent;
import consulo.internal.com.sun.jdi.request.ExceptionRequest;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public class ExceptionBreakpoint extends Breakpoint<JavaExceptionBreakpointProperties> {
    private static final Logger LOG = Logger.getInstance(ExceptionBreakpoint.class);

    protected final static String READ_NO_CLASS_NAME = DebuggerBundle.message("error.absent.exception.breakpoint.class.name");
    public static final
    @NonNls
    Key<ExceptionBreakpoint> CATEGORY = BreakpointCategory.lookup("exception_breakpoints");

    public ExceptionBreakpoint(Project project, XBreakpoint<JavaExceptionBreakpointProperties> xBreakpoint) {
        super(project, xBreakpoint);
    }

    @Override
    public Key<? extends ExceptionBreakpoint> getCategory() {
        return CATEGORY;
    }

    protected ExceptionBreakpoint(Project project, String qualifiedName, String packageName, XBreakpoint<JavaExceptionBreakpointProperties>
        xBreakpoint) {
        super(project, xBreakpoint);
        setQualifiedName(qualifiedName);
        if (packageName == null) {
            setPackageName(calcPackageName(qualifiedName));
        }
        else {
            setPackageName(packageName);
        }
    }

    private static String calcPackageName(String qualifiedName) {
        if (qualifiedName == null) {
            return null;
        }
        int dotIndex = qualifiedName.lastIndexOf('.');
        return dotIndex >= 0 ? qualifiedName.substring(0, dotIndex) : "";
    }

    @Override
    public String getClassName() {
        return getQualifiedName();
    }

    @Override
    public String getPackageName() {
        return getProperties().myPackageName;
    }

    @Override
    public PsiClass getPsiClass() {
        return PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Computable<PsiClass>() {
            @Override
            public PsiClass compute() {
                return getQualifiedName() != null ? DebuggerUtils.findClass(getQualifiedName(), myProject, GlobalSearchScope.allScope(myProject)) :
                    null;
            }
        });
    }

    @Override
    public String getDisplayName() {
        return DebuggerBundle.message("breakpoint.exception.breakpoint.display.name", getQualifiedName());
    }

    @Override
    public Image getIcon() {
        if (!isEnabled()) {
            final Breakpoint master = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this);
            return master == null ? ExecutionDebugIconGroup.breakpointBreakpointexceptiondisabled() : ExecutionDebugIconGroup.breakpointBreakpointexception();
        }
        return ExecutionDebugIconGroup.breakpointBreakpointexception();
    }

    @Override
    public void reload() {
    }

    @Override
    public void createRequest(final DebugProcessImpl debugProcess) {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        if (!shouldCreateRequest(debugProcess)) {
            return;
        }

        SourcePosition classPosition = ApplicationManager.getApplication().runReadAction(new Computable<SourcePosition>() {
            @Override
            public SourcePosition compute() {
                PsiClass psiClass = DebuggerUtils.findClass(getQualifiedName(), myProject, debugProcess.getSearchScope());

                return psiClass != null ? SourcePosition.createFromElement(psiClass) : null;
            }
        });

        if (classPosition == null) {
            createOrWaitPrepare(debugProcess, getQualifiedName());
        }
        else {
            createOrWaitPrepare(debugProcess, classPosition);
        }
    }

    @Override
    public void processClassPrepare(DebugProcess process, ReferenceType refType) {
        DebugProcessImpl debugProcess = (DebugProcessImpl) process;
        if (!isEnabled()) {
            return;
        }
        // trying to create a request
        ExceptionRequest request = debugProcess.getRequestsManager().createExceptionRequest(this, refType, isNotifyCaught(), isNotifyUncaught());
        debugProcess.getRequestsManager().enableRequest(request);
        if (LOG.isDebugEnabled()) {
            if (refType != null) {
                LOG.debug("Created exception request for reference type " + refType.name());
            }
            else {
                LOG.debug("Created exception request for reference type null");
            }
        }
    }

    @Override
    protected ObjectReference getThisObject(SuspendContextImpl context, LocatableEvent event) throws EvaluateException {
        if (event instanceof ExceptionEvent) {
            return ((ExceptionEvent) event).exception();
        }
        return super.getThisObject(context, event);    //To change body of overriden methods use Options | File Templates.
    }

    @Override
    public String getEventMessage(LocatableEvent event) {
        String exceptionName = (getQualifiedName() != null) ? getQualifiedName() : CommonClassNames.JAVA_LANG_THROWABLE;
        String threadName = null;
        if (event instanceof ExceptionEvent) {
            ExceptionEvent exceptionEvent = (ExceptionEvent) event;
            try {
                exceptionName = exceptionEvent.exception().type().name();
                threadName = exceptionEvent.thread().name();
            }
            catch (Exception ignore) {
            }
        }
        final Location location = event.location();
        final String locationQName = location.declaringType().name() + "." + location.method().name();
        String locationFileName;
        try {
            locationFileName = location.sourceName();
        }
        catch (AbsentInformationException e) {
            locationFileName = "";
        }
        final int locationLine = Math.max(0, location.lineNumber());
        if (threadName != null) {
            return DebuggerBundle.message("exception.breakpoint.console.message.with.thread.info", exceptionName, threadName, locationQName,
                locationFileName, locationLine);
        }
        else {
            return DebuggerBundle.message("exception.breakpoint.console.message", exceptionName, locationQName, locationFileName, locationLine);
        }
    }

    @Override
    public boolean isValid() {
        return true;
    }

    //@SuppressWarnings({"HardCodedStringLiteral"}) public void writeExternal(Element parentNode) throws WriteExternalException {
    //  super.writeExternal(parentNode);
    //  if(getQualifiedName() != null) {
    //    parentNode.setAttribute("class_name", getQualifiedName());
    //  }
    //  if(getPackageName() != null) {
    //    parentNode.setAttribute("package_name", getPackageName());
    //  }
    //}

    @Override
    public PsiElement getEvaluationElement() {
        if (getClassName() == null) {
            return null;
        }
        return JavaPsiFacade.getInstance(myProject).findClass(getClassName(), GlobalSearchScope.allScope(myProject));
    }

    @Override
    public void readExternal(Element parentNode) throws InvalidDataException {
        super.readExternal(parentNode);

        //noinspection HardCodedStringLiteral
        String packageName = parentNode.getAttributeValue("package_name");
        setPackageName(packageName != null ? packageName : calcPackageName(packageName));

        try {
            getProperties().NOTIFY_CAUGHT = Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "NOTIFY_CAUGHT"));
        }
        catch (Exception ignore) {
        }
        try {
            getProperties().NOTIFY_UNCAUGHT = Boolean.valueOf(JDOMExternalizerUtil.readField(parentNode, "NOTIFY_UNCAUGHT"));
        }
        catch (Exception ignore) {
        }

        //noinspection HardCodedStringLiteral
        String className = parentNode.getAttributeValue("class_name");
        setQualifiedName(className);
        if (className == null) {
            throw new InvalidDataException(READ_NO_CLASS_NAME);
        }
    }

    private boolean isNotifyCaught() {
        return getProperties().NOTIFY_CAUGHT;
    }

    private boolean isNotifyUncaught() {
        return getProperties().NOTIFY_UNCAUGHT;
    }

    private String getQualifiedName() {
        return getProperties().myQualifiedName;
    }

    void setQualifiedName(String qualifiedName) {
        getProperties().myQualifiedName = qualifiedName;
    }

    void setPackageName(String packageName) {
        getProperties().myPackageName = packageName;
    }
}
