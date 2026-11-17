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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.*;
import com.intellij.java.debugger.impl.actions.JvmDropFrameActionHandler;
import com.intellij.java.debugger.impl.actions.JvmSmartStepIntoActionHandler;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadGroupReferenceProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import com.intellij.java.debugger.impl.memory.JavaMemoryViewHandler;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.settings.ThreadsViewSettings;
import com.intellij.java.debugger.impl.ui.AlternativeSourceNotificationProvider;
import com.intellij.java.debugger.impl.ui.breakpoints.Breakpoint;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.MessageDescriptor;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeManagerImpl;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import consulo.execution.debug.*;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.XBreakpointHandler;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.execution.debug.event.XDebugSessionListener;
import consulo.execution.debug.frame.HasDropFrame;
import consulo.execution.debug.frame.HasRunningExecutionStacks;
import consulo.execution.debug.frame.XDropFrameHandler;
import consulo.execution.debug.frame.XExecutionStack;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.frame.XSuspendContext;
import consulo.execution.debug.frame.XValueMarkerProvider;
import consulo.execution.debug.localize.XDebuggerLocalize;
import consulo.execution.debug.memory.HasMemoryView;
import consulo.execution.debug.memory.XMemoryViewHandler;
import consulo.execution.debug.step.HasForceStepInto;
import consulo.execution.debug.step.HasRunToPosition;
import consulo.execution.debug.step.HasSmartStepInto;
import consulo.execution.debug.step.XSmartStepIntoHandler;
import consulo.execution.debug.ui.DebuggerContentInfo;
import consulo.execution.debug.ui.XDebugTabLayouter;
import consulo.execution.ui.ExecutionConsole;
import consulo.execution.ui.ExecutionConsoleEx;
import consulo.execution.ui.layout.RunnerLayoutUi;
import consulo.fileEditor.EditorNotifications;
import consulo.internal.com.sun.jdi.event.Event;
import consulo.localize.LocalizeValue;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.*;
import consulo.ui.ex.content.Content;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author egor
 */
public class JavaDebugProcess extends XDebugProcess
    implements HasForceStepInto, HasRunToPosition, HasSmartStepInto, HasDropFrame, HasRunningExecutionStacks, HasMemoryView {
    private final DebuggerSession myJavaSession;
    private final JavaDebuggerEditorsProvider myEditorsProvider;
    private final XBreakpointHandler<?>[] myBreakpointHandlers;
    private final JvmSmartStepIntoActionHandler mySmartStepIntoActionHandler;
    private final JvmDropFrameActionHandler myDropFrameActionActionHandler;
    private final NodeManagerImpl myNodeManager;
    private final JavaMemoryViewHandler myMemoryViewHandler;

    public static JavaDebugProcess create(XDebugSession session, DebuggerSession javaSession) {
        JavaDebugProcess res = new JavaDebugProcess(session, javaSession);
        javaSession.getProcess().setXDebugProcess(res);
        return res;
    }

    protected JavaDebugProcess(XDebugSession session, DebuggerSession javaSession) {
        super(session);
        myJavaSession = javaSession;
        myEditorsProvider = new JavaDebuggerEditorsProvider();
        DebugProcessImpl process = javaSession.getProcess();

        List<XBreakpointHandler> handlers = new ArrayList<>();
        handlers.add(new JavaBreakpointHandler.JavaLineBreakpointHandler(process));
        handlers.add(new JavaBreakpointHandler.JavaExceptionBreakpointHandler(process));
        handlers.add(new JavaBreakpointHandler.JavaFieldBreakpointHandler(process));
        handlers.add(new JavaBreakpointHandler.JavaMethodBreakpointHandler(process));
        handlers.add(new JavaBreakpointHandler.JavaWildcardBreakpointHandler(process));

        for (JavaBreakpointHandlerFactory factory : JavaBreakpointHandlerFactory.EP_NAME.getExtensionList()) {
            handlers.add(factory.createHandler(process));
        }

        myBreakpointHandlers = handlers.toArray(new XBreakpointHandler[handlers.size()]);

        myJavaSession.getContextManager().addListener((newContext, event) -> {
            if (event == DebuggerSession.Event.PAUSE || event == DebuggerSession.Event.CONTEXT
                || event == DebuggerSession.Event.REFRESH && myJavaSession.isPaused()) {
                SuspendContextImpl newSuspendContext = newContext.getSuspendContext();
                if (newSuspendContext != null && shouldApplyContext(newContext)) {
                    process.getManagerThread().schedule(new SuspendContextCommandImpl(newSuspendContext) {
                        @Override
                        public void contextAction() throws Exception {
                            newSuspendContext.initExecutionStacks(newContext.getThreadProxy());

                            List<Pair<Breakpoint, Event>> descriptors = DebuggerUtilsEx.getEventDescriptors(newSuspendContext);
                            if (!descriptors.isEmpty()) {
                                Breakpoint breakpoint = descriptors.get(0).getFirst();
                                XBreakpoint xBreakpoint = breakpoint.getXBreakpoint();
                                if (xBreakpoint != null) {
                                    getSession().breakpointReachedNoProcessing(xBreakpoint, newSuspendContext);
                                    unsetPausedIfNeeded(newContext);
                                    return;
                                }
                            }
                            getSession().positionReached(newSuspendContext);
                            unsetPausedIfNeeded(newContext);
                        }
                    });
                }
            }
            else if (event == DebuggerSession.Event.ATTACHED) {
                getSession().rebuildViews(); // to refresh variables views message
            }
        });

        myNodeManager = new NodeManagerImpl(session.getProject(), null) {
            @Override
            public DebuggerTreeNodeImpl createNode(NodeDescriptor descriptor, EvaluationContext evaluationContext) {
                return new DebuggerTreeNodeImpl(null, descriptor);
            }

            @Override
            public DebuggerTreeNodeImpl createMessageNode(MessageDescriptor descriptor) {
                return new DebuggerTreeNodeImpl(null, descriptor);
            }

            @Override
            public DebuggerTreeNodeImpl createMessageNode(LocalizeValue message) {
                return new DebuggerTreeNodeImpl(null, new MessageDescriptor(message));
            }
        };
        myMemoryViewHandler = new JavaMemoryViewHandler(javaSession.getProcess(), myNodeManager);
        session.addSessionListener(new XDebugSessionListener() {
            @Override
            public void sessionPaused() {
                saveNodeHistory();
                showAlternativeNotification(session.getCurrentStackFrame());
            }

            @Override
            public void stackFrameChanged() {
                XStackFrame frame = session.getCurrentStackFrame();
                if (frame instanceof JavaStackFrame javaStackFrame) {
                    showAlternativeNotification(frame);
                    StackFrameProxyImpl frameProxy = javaStackFrame.getStackFrameProxy();
                    DebuggerContextUtil.setStackFrame(javaSession.getContextManager(), frameProxy);
                    saveNodeHistory(frameProxy);
                }
            }

            private void showAlternativeNotification(@Nullable XStackFrame frame) {
                if (frame != null) {
                    XSourcePosition position = frame.getSourcePosition();
                    if (position != null) {
                        VirtualFile file = position.getFile();
                        if (!AlternativeSourceNotificationProvider.fileProcessed(file)) {
                            EditorNotifications.getInstance(session.getProject()).updateNotifications(file);
                        }
                    }
                }
            }
        });

        mySmartStepIntoActionHandler = new JvmSmartStepIntoActionHandler(myJavaSession);
        myDropFrameActionActionHandler = new JvmDropFrameActionHandler(javaSession);
    }

    private void unsetPausedIfNeeded(DebuggerContextImpl context) {
        SuspendContextImpl suspendContext = context.getSuspendContext();
        if (suspendContext != null && !suspendContext.suspends(context.getThreadProxy())) {
            getSession().unsetPaused();
        }
    }

    private boolean shouldApplyContext(DebuggerContextImpl context) {
        SuspendContextImpl suspendContext = context.getSuspendContext();
        SuspendContextImpl currentContext = (SuspendContextImpl)getSession().getSuspendContext();
        if (suspendContext != null && !suspendContext.equals(currentContext)) {
            return true;
        }
        JavaExecutionStack currentExecutionStack = currentContext != null ? currentContext.getActiveExecutionStack() : null;
        return currentExecutionStack == null || !Comparing.equal(context.getThreadProxy(), currentExecutionStack.getThreadProxy());
    }

    public void saveNodeHistory() {
        saveNodeHistory(getDebuggerStateManager().getContext().getFrameProxy());
    }

    private void saveNodeHistory(StackFrameProxyImpl frameProxy) {
        myJavaSession.getProcess().getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                myNodeManager.setHistoryByContext(frameProxy);
            }

            @Override
            public Priority getPriority() {
                return Priority.NORMAL;
            }
        });
    }

    private DebuggerStateManager getDebuggerStateManager() {
        return myJavaSession.getContextManager();
    }

    public DebuggerSession getDebuggerSession() {
        return myJavaSession;
    }

    @Override
    public XMemoryViewHandler getMemoryViewHandler() {
        return myMemoryViewHandler;
    }

    @Override
    public void computeRunningExecutionStacks(XSuspendContext.XExecutionStackGroupContainer container, @Nullable XSuspendContext suspendContext) {
        boolean showThreadGroups = ThreadsViewSettings.getInstance().SHOW_THREAD_GROUPS;
        if (!showThreadGroups && suspendContext != null) {
            suspendContext.computeExecutionStacks(container);
            return;
        }
        DebugProcessImpl debugProcess = myJavaSession.getProcess();
        debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
            @Override
            protected void action() {
                try {
                    if (showThreadGroups) {
                        addExecutionStackGroups(container, debugProcess);
                    }
                    else {
                        addRunningExecutionStacks(container, debugProcess);
                    }
                }
                catch (Throwable e) {
                    container.errorOccurred(LocalizeValue.join(
                        XDebuggerLocalize.debuggerThreadsNotAvailable(),
                        LocalizeValue.of(": " + e.getMessage())
                    ));
                }
            }

            @Override
            protected void commandCancelled() {
                container.errorOccurred(XDebuggerLocalize.debuggerThreadsNotAvailable());
            }
        });
    }

    private static void addExecutionStackGroups(XSuspendContext.XExecutionStackGroupContainer container, DebugProcessImpl debugProcess) {
        ThreadReferenceProxyImpl currentThread = debugProcess.getDebuggerContext().getThreadProxy();
        List<CompletableFuture<JavaThreadGroup>> groups = new ArrayList<>();
        for (ThreadGroupReferenceProxyImpl group : debugProcess.getVirtualMachineProxy().topLevelThreadGroups()) {
            groups.add(JavaThreadGroup.build(group, debugProcess, currentThread));
        }
        CompletableFuture.allOf(groups.toArray(CompletableFuture[]::new)).whenComplete((unused, throwable) -> {
            if (throwable != null) {
                DebuggerUtilsAsync.logError(throwable);
                return;
            }
            if (container.isObsolete()) {
                return;
            }
            container.addExecutionStackGroups(ContainerUtil.map(groups, CompletableFuture::join), true);
        });
    }

    private static void addRunningExecutionStacks(XSuspendContext.XExecutionStackGroupContainer container, DebugProcessImpl debugProcess) {
        List<XExecutionStack> executionStacks = new ArrayList<>();
        for (ThreadReferenceProxyImpl thread : debugProcess.getVirtualMachineProxy().allThreads()) {
            executionStacks.add(new JavaExecutionStack(thread, debugProcess, false));
        }
        container.addExecutionStack(executionStacks, true);
    }

    @Override
    public XDebuggerEditorsProvider getEditorsProvider() {
        return myEditorsProvider;
    }

    @Override
    @RequiredUIAccess
    public void startStepOver() {
        myJavaSession.stepOver(false);
    }

    @Override
    @RequiredUIAccess
    public void startStepInto() {
        myJavaSession.stepInto(false, null);
    }

    @Override
    @RequiredUIAccess
    public void startForceStepInto(@Nullable XSuspendContext context) {
        myJavaSession.stepInto(true, null);
    }

    @Override
    @RequiredUIAccess
    public void startStepOut() {
        myJavaSession.stepOut();
    }

    @Override
    public void stop() {
        myJavaSession.dispose();
        myNodeManager.dispose();
    }

    @Override
    public void startPausing() {
        myJavaSession.pause();
    }

    @Override
    @RequiredUIAccess
    public void resume() {
        myJavaSession.resume();
    }

    @Override
    @RequiredUIAccess
    public void runToPosition(XSourcePosition position, @Nullable XSuspendContext context) {
        myJavaSession.runToCursor(position, false);
    }

    @Override
    public XBreakpointHandler<?>[] getBreakpointHandlers() {
        return myBreakpointHandlers;
    }

    @Override
    public boolean checkCanInitBreakpoints() {
        return false;
    }

    @Nullable
    @Override
    protected ProcessHandler doGetProcessHandler() {
        return myJavaSession.getProcess().getProcessHandler();
    }

    @Override
    public ExecutionConsole createConsole() {
        ExecutionConsole console = myJavaSession.getProcess().getExecutionResult().getExecutionConsole();
        if (console != null) {
            return console;
        }
        return super.createConsole();
    }

    @Override
    public XDebugTabLayouter createTabLayouter() {
        return new XDebugTabLayouter() {
            @Override
            public void registerAdditionalContent(RunnerLayoutUi ui) {
                //registerMemoryViewPanel(ui);
            }

            @Override
            public Content registerConsoleContent(RunnerLayoutUi ui, ExecutionConsole console) {
                Content content = null;
                if (console instanceof ExecutionConsoleEx consoleEx) {
                    consoleEx.buildUi(ui);
                    content = ui.findContent(DebuggerContentInfo.CONSOLE_CONTENT);
                }
                if (content == null) {
                    content = super.registerConsoleContent(ui, console);
                }
                return content;
            }

//            private void registerMemoryViewPanel(RunnerLayoutUi ui) {
//                XDebugSession session = getSession();
//                DebugProcessImpl process = myJavaSession.getProcess();
//                InstancesTracker tracker = InstancesTracker.getInstance(myJavaSession.getProject());
//
//                ClassesFilteredView classesFilteredView = new ClassesFilteredView(session, process, tracker);
//
//                Content memoryViewContent = ui.createContent(MemoryViewManager.MEMORY_VIEW_CONTENT,
//                    classesFilteredView,
//                    "Memory View",
//                    ExecutionDebugIconGroup.nodeMemory(),
//                    null);
//
//                memoryViewContent.setCloseable(false);
//                memoryViewContent.setShouldDisposeContent(true);
//
//                MemoryViewDebugProcessData data = new MemoryViewDebugProcessData();
//                process.putUserData(MemoryViewDebugProcessData.KEY, data);
//                session.addSessionListener(new XDebugSessionListener() {
//                    @Override
//                    public void sessionStopped() {
//                        session.removeSessionListener(this);
//                        data.getTrackedStacks().clear();
//                    }
//                });
//
//                ui.addContent(memoryViewContent, 0, PlaceInGrid.right, true);
//                DebuggerManagerThreadImpl managerThread = process.getManagerThread();
//                ui.addListener(
//                    new ContentManagerAdapter() {
//                        @Override
//                        public void selectionChanged(ContentManagerEvent event) {
//                            if (event != null && event.getContent() == memoryViewContent) {
//                                classesFilteredView.setActive(memoryViewContent.isSelected(), managerThread);
//                            }
//                        }
//                    },
//                    memoryViewContent
//                );
//            }
        };
    }

    @Override
    public void registerAdditionalActions(
        DefaultActionGroup moreGroup,
        DefaultActionGroup topToolbar,
        DefaultActionGroup settings
    ) {
        Constraints beforeSort = new Constraints(Anchor.BEFORE, "XDebugger.ToggleSortValues");
        settings.addAction(new WatchLastMethodReturnValueAction(), beforeSort);
        settings.addAction(new AutoVarsSwitchAction(), beforeSort);
    }

    private static class AutoVarsSwitchAction extends ToggleAction {
        private volatile boolean myAutoModeEnabled;

        public AutoVarsSwitchAction() {
            super(
                JavaDebuggerLocalize.actionAutoVariablesMode(),
                JavaDebuggerLocalize.actionAutoVariablesModeDescription()
            );
            myAutoModeEnabled = DebuggerSettings.getInstance().AUTO_VARIABLES_MODE;
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return myAutoModeEnabled;
        }

        @Override
        public void setSelected(AnActionEvent e, boolean enabled) {
            myAutoModeEnabled = enabled;
            DebuggerSettings.getInstance().AUTO_VARIABLES_MODE = enabled;
            XDebuggerUtil.getInstance().rebuildAllSessionsViews(e.getData(Project.KEY));
        }
    }

    private static class WatchLastMethodReturnValueAction extends ToggleAction {
        private volatile boolean myWatchesReturnValues;
        private final LocalizeValue myText;
        private final LocalizeValue myTextUnavailable;

        public WatchLastMethodReturnValueAction() {
            super(LocalizeValue.empty(), JavaDebuggerLocalize.actionWatchMethodReturnValueDescription());
            myWatchesReturnValues = DebuggerSettings.getInstance().WATCH_RETURN_VALUES;
            myText = JavaDebuggerLocalize.actionWatchesMethodReturnValueEnable();
            myTextUnavailable = JavaDebuggerLocalize.actionWatchesMethodReturnValueUnavailableReason();
        }

        @RequiredUIAccess
        @Override
        public void update(AnActionEvent e) {
            super.update(e);
            Presentation presentation = e.getPresentation();
            DebugProcessImpl process = getCurrentDebugProcess(e.getData(Project.KEY));
            if (process == null || process.canGetMethodReturnValue()) {
                presentation.setEnabled(true);
                presentation.setText(myText);
            }
            else {
                presentation.setEnabled(false);
                presentation.setText(myTextUnavailable);
            }
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return myWatchesReturnValues;
        }

        @Override
        public void setSelected(AnActionEvent e, boolean watch) {
            myWatchesReturnValues = watch;
            DebuggerSettings.getInstance().WATCH_RETURN_VALUES = watch;
            DebugProcessImpl process = getCurrentDebugProcess(e.getData(Project.KEY));
            if (process != null) {
                process.setWatchMethodReturnValuesEnabled(watch);
            }
        }
    }

    @Nullable
    private static DebugProcessImpl getCurrentDebugProcess(@Nullable Project project) {
        if (project != null) {
            XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
            if (session != null && session.getDebugProcess() instanceof JavaDebugProcess debugProcess) {
                return debugProcess.getDebuggerSession().getProcess();
            }
        }
        return null;
    }

    public NodeManagerImpl getNodeManager() {
        return myNodeManager;
    }

    @Override
    public LocalizeValue getCurrentStateMessage() {
        LocalizeValue description = myJavaSession.getStateDescription();
        return description.isNotEmpty() ? description : super.getCurrentStateMessage();
    }

    @Nullable
    @Override
    public XValueMarkerProvider<?, ?> createValueMarkerProvider() {
        return new JavaValueMarker();
    }

    @Override
    public XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
        return mySmartStepIntoActionHandler;
    }

    @Override
    public XDropFrameHandler getDropFrameHandler() {
        return myDropFrameActionActionHandler;
    }
}
