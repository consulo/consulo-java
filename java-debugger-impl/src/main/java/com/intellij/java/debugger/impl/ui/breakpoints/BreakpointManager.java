/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * Class BreakpointManager
 * @author Jeka
 */
package com.intellij.java.debugger.impl.ui.breakpoints;

import com.intellij.java.debugger.impl.*;
import com.intellij.java.debugger.impl.engine.BreakpointStepMethodFilter;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.requests.RequestManagerImpl;
import com.intellij.java.language.psi.PsiField;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.execution.debug.XBreakpointManager;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.XDebuggerUtil;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.breakpoint.*;
import consulo.execution.debug.ui.XDebuggerUIConstants;
import consulo.internal.com.sun.jdi.InternalException;
import consulo.internal.com.sun.jdi.ThreadReference;
import consulo.internal.com.sun.jdi.request.*;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationType;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Supplier;

public class BreakpointManager {
    private static final Logger LOG = Logger.getInstance(BreakpointManager.class);

    private final Project myProject;

    public BreakpointManager(@Nonnull Project project, @Nonnull DebuggerManagerImpl debuggerManager) {
        myProject = project;

        debuggerManager.getContextManager().addListener(new DebuggerContextListener() {
            private DebuggerSession myPreviousSession;

            @Override
            public void changeEvent(@Nonnull DebuggerContextImpl newContext, DebuggerSession.Event event) {
                if (event == DebuggerSession.Event.ATTACHED) {
                    for (XBreakpoint breakpoint : getXBreakpointManager().getAllBreakpoints()) {
                        if (checkAndNotifyPossiblySlowBreakpoint(breakpoint)) {
                            break;
                        }
                    }
                }
                if (newContext.getDebuggerSession() != myPreviousSession || event == DebuggerSession.Event.DETACHED) {
                    updateBreakpointsUI();
                    myPreviousSession = newContext.getDebuggerSession();
                }
            }
        });
    }

    private static boolean checkAndNotifyPossiblySlowBreakpoint(XBreakpoint breakpoint) {
        if (breakpoint.isEnabled() && (breakpoint.getType() instanceof JavaMethodBreakpointType || breakpoint.getType() instanceof JavaWildcardMethodBreakpointType)) {
            XDebuggerUIConstants.NOTIFICATION_GROUP.createNotification("Method breakpoints may dramatically slow down debugging", NotificationType.WARNING).notify((breakpoint).getProject());
            return true;
        }
        return false;
    }

    private XBreakpointManager getXBreakpointManager() {
        return XDebuggerManager.getInstance(myProject).getBreakpointManager();
    }

    public void editBreakpoint(final Breakpoint breakpoint, final Editor editor) {
        DebuggerInvocationUtil.swingInvokeLater(myProject, () -> {
            XBreakpoint xBreakpoint = breakpoint.myXBreakpoint;
            if (xBreakpoint instanceof XLineBreakpoint) {
                RangeHighlighter highlighter = ((XLineBreakpoint) xBreakpoint).getHighlighter();
                if (highlighter != null) {
                    GutterIconRenderer renderer = highlighter.getGutterIconRenderer();
                    if (renderer != null) {
                        XDebuggerManager.getInstance(myProject).getBreakpointManager().editBreakpoint(myProject, editor, breakpoint.myXBreakpoint, renderer);
                    }
                }
            }
        });
    }

    @Nullable
    public RunToCursorBreakpoint addRunToCursorBreakpoint(@Nonnull XSourcePosition position, final boolean ignoreBreakpoints) {
        return RunToCursorBreakpoint.create(myProject, position, ignoreBreakpoints);
    }

    @Nullable
    public StepIntoBreakpoint addStepIntoBreakpoint(@Nonnull BreakpointStepMethodFilter filter) {
        return StepIntoBreakpoint.create(myProject, filter);
    }

    @Nullable
    public FieldBreakpoint addFieldBreakpoint(@Nonnull Document document, int offset) {
        PsiField field = FieldBreakpoint.findField(myProject, document, offset);
        if (field == null) {
            return null;
        }

        int line = document.getLineNumber(offset);

        if (document.getLineNumber(field.getNameIdentifier().getTextOffset()) < line) {
            return null;
        }

        return addFieldBreakpoint(document, line, field.getName());
    }

    @Nullable
    public FieldBreakpoint addFieldBreakpoint(Document document, int lineIndex, String fieldName) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        XLineBreakpoint xBreakpoint = addXLineBreakpoint(JavaFieldBreakpointType.class, document, lineIndex);
        Breakpoint javaBreakpoint = getJavaBreakpoint(xBreakpoint);
        if (javaBreakpoint instanceof FieldBreakpoint) {
            FieldBreakpoint fieldBreakpoint = (FieldBreakpoint) javaBreakpoint;
            fieldBreakpoint.setFieldName(fieldName);
            addBreakpoint(javaBreakpoint);
            return fieldBreakpoint;
        }
        return null;
    }

    @Nullable
    public MethodBreakpoint addMethodBreakpoint(Document document, int lineIndex) {
        ApplicationManager.getApplication().assertIsDispatchThread();

        XLineBreakpoint xBreakpoint = addXLineBreakpoint(JavaMethodBreakpointType.class, document, lineIndex);
        Breakpoint javaBreakpoint = getJavaBreakpoint(xBreakpoint);
        if (javaBreakpoint instanceof MethodBreakpoint) {
            addBreakpoint(javaBreakpoint);
            return (MethodBreakpoint) javaBreakpoint;
        }
        return null;
    }

    private <B extends XBreakpoint<?>> XLineBreakpoint addXLineBreakpoint(Class<? extends XBreakpointType<B, ?>> typeCls, Document document, final int lineIndex) {
        final XBreakpointType<B, ?> type = XDebuggerUtil.getInstance().findBreakpointType(typeCls);
        final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        return ApplicationManager.getApplication().runWriteAction(new Supplier<XLineBreakpoint>() {
            @Override
            public XLineBreakpoint get() {
                return XDebuggerManager.getInstance(myProject).getBreakpointManager().addLineBreakpoint((XLineBreakpointType) type, file.getUrl(), lineIndex,
                    ((XLineBreakpointType) type).createBreakpointProperties(file, lineIndex));
            }
        });
    }

    /**
     * @param category breakpoint category, null if the category does not matter
     */
    @Nullable
    public <T extends BreakpointWithHighlighter> T findBreakpoint(final Document document, final int offset, @Nullable final Key<T> category) {
        for (final Breakpoint breakpoint : getBreakpoints()) {
            if (breakpoint instanceof BreakpointWithHighlighter && ((BreakpointWithHighlighter) breakpoint).isAt(document, offset)) {
                if (category == null || category.equals(breakpoint.getCategory())) {
                    //noinspection CastConflictsWithInstanceof,unchecked
                    return (T) breakpoint;
                }
            }
        }
        return null;
    }

    public static void addBreakpoint(@Nonnull Breakpoint breakpoint) {
        assert breakpoint.myXBreakpoint.getUserData(Breakpoint.DATA_KEY) == breakpoint;
        breakpoint.updateUI();
        checkAndNotifyPossiblySlowBreakpoint(breakpoint.myXBreakpoint);
    }

    public void removeBreakpoint(@Nullable final Breakpoint breakpoint) {
        if (breakpoint == null) {
            return;
        }

        ApplicationManager.getApplication().runWriteAction(() -> getXBreakpointManager().removeBreakpoint(breakpoint.myXBreakpoint));
    }

    @Nonnull
    public List<Breakpoint> getBreakpoints() {
        return ReadAction.compute(() -> ContainerUtil.mapNotNull(getXBreakpointManager().getAllBreakpoints(), BreakpointManager::getJavaBreakpoint));
    }

    @Nullable
    public static Breakpoint getJavaBreakpoint(@Nullable final XBreakpoint xBreakpoint) {
        if (xBreakpoint == null) {
            return null;
        }
        Breakpoint breakpoint = xBreakpoint.getUserData(Breakpoint.DATA_KEY);
        if (breakpoint == null && xBreakpoint.getType() instanceof JavaBreakpointType) {
            Project project = xBreakpoint.getProject();
            breakpoint = ((JavaBreakpointType) xBreakpoint.getType()).createJavaBreakpoint(project, xBreakpoint);
            xBreakpoint.putUserData(Breakpoint.DATA_KEY, breakpoint);
        }
        return breakpoint;
    }

    //interaction with RequestManagerImpl
    public void disableBreakpoints(@Nonnull final DebugProcessImpl debugProcess) {
        final List<Breakpoint> breakpoints = getBreakpoints();
        if (!breakpoints.isEmpty()) {
            final RequestManagerImpl requestManager = debugProcess.getRequestsManager();
            for (Breakpoint breakpoint : breakpoints) {
                breakpoint.markVerified(requestManager.isVerified(breakpoint));
                requestManager.deleteRequest(breakpoint);
            }
            SwingUtilities.invokeLater(() -> updateBreakpointsUI());
        }
    }

    public void enableBreakpoints(final DebugProcessImpl debugProcess) {
        final List<Breakpoint> breakpoints = getBreakpoints();
        if (!breakpoints.isEmpty()) {
            for (Breakpoint breakpoint : breakpoints) {
                breakpoint.markVerified(false); // clean cached state
                breakpoint.createRequest(debugProcess);
            }
            SwingUtilities.invokeLater(() -> updateBreakpointsUI());
        }
    }

    public void applyThreadFilter(@Nonnull final DebugProcessImpl debugProcess, @Nullable ThreadReference newFilterThread) {
        final RequestManagerImpl requestManager = debugProcess.getRequestsManager();
        final ThreadReference oldFilterThread = requestManager.getFilterThread();
        if (Comparing.equal(newFilterThread, oldFilterThread)) {
            // the filter already added
            return;
        }
        requestManager.setFilterThread(newFilterThread);
        if (newFilterThread == null || oldFilterThread != null) {
            final List<Breakpoint> breakpoints = getBreakpoints();
            for (Breakpoint breakpoint : breakpoints) {
                if (LineBreakpoint.CATEGORY.equals(breakpoint.getCategory()) || MethodBreakpoint.CATEGORY.equals(breakpoint.getCategory())) {
                    requestManager.deleteRequest(breakpoint);
                    breakpoint.createRequest(debugProcess);
                }
            }
        }
        else {
            // important! need to add filter to _existing_ requests, otherwise Requestor->Request mapping will be lost
            // and debugger trees will not be restored to original state
            abstract class FilterSetter<T extends EventRequest> {
                void applyFilter(@Nonnull final List<T> requests, final ThreadReference thread) {
                    for (T request : requests) {
                        try {
                            final boolean wasEnabled = request.isEnabled();
                            if (wasEnabled) {
                                request.disable();
                            }
                            addFilter(request, thread);
                            if (wasEnabled) {
                                request.enable();
                            }
                        }
                        catch (InternalException | InvalidRequestStateException e) {
                            LOG.info(e);
                        }
                    }
                }

                protected abstract void addFilter(final T request, final ThreadReference thread);
            }

            final EventRequestManager eventRequestManager = requestManager.getVMRequestManager();
            if (eventRequestManager != null) {
                new FilterSetter<BreakpointRequest>() {
                    @Override
                    protected void addFilter(@Nonnull final BreakpointRequest request, final ThreadReference thread) {
                        request.addThreadFilter(thread);
                    }
                }.applyFilter(eventRequestManager.breakpointRequests(), newFilterThread);

                new FilterSetter<MethodEntryRequest>() {
                    @Override
                    protected void addFilter(@Nonnull final MethodEntryRequest request, final ThreadReference thread) {
                        request.addThreadFilter(thread);
                    }
                }.applyFilter(eventRequestManager.methodEntryRequests(), newFilterThread);

                new FilterSetter<MethodExitRequest>() {
                    @Override
                    protected void addFilter(@Nonnull final MethodExitRequest request, final ThreadReference thread) {
                        request.addThreadFilter(thread);
                    }
                }.applyFilter(eventRequestManager.methodExitRequests(), newFilterThread);
            }
        }
    }

    public void updateBreakpointsUI() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        for (Breakpoint breakpoint : getBreakpoints()) {
            breakpoint.updateUI();
        }
    }

    public void reloadBreakpoints() {
        ApplicationManager.getApplication().assertIsDispatchThread();

        for (Breakpoint breakpoint : getBreakpoints()) {
            breakpoint.reload();
        }
    }

    public static void fireBreakpointChanged(Breakpoint breakpoint) {
        breakpoint.reload();
        breakpoint.updateUI();
    }

    public void setBreakpointEnabled(@Nonnull final Breakpoint breakpoint, final boolean enabled) {
        if (breakpoint.isEnabled() != enabled) {
            breakpoint.setEnabled(enabled);
        }
    }

    @Nullable
    public Breakpoint findMasterBreakpoint(@Nonnull Breakpoint dependentBreakpoint) {
        XDependentBreakpointManager dependentBreakpointManager = getXBreakpointManager().getDependentBreakpointManager();
        return getJavaBreakpoint(dependentBreakpointManager.getMasterBreakpoint(dependentBreakpoint.myXBreakpoint));
    }
}
