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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.DebuggerManager;
import com.intellij.java.debugger.PositionManagerFactory;
import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.impl.DebuggerInvocationUtil;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerManagerImpl;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.engine.requests.LocatableEventRequestor;
import com.intellij.java.debugger.impl.engine.requests.MethodReturnValueWatcher;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.ui.breakpoints.Breakpoint;
import com.intellij.java.debugger.impl.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.debugger.requests.Requestor;
import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.application.Application;
import consulo.component.ProcessCanceledException;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.ui.XDebuggerUIConstants;
import consulo.internal.com.sun.jdi.InternalException;
import consulo.internal.com.sun.jdi.ThreadReference;
import consulo.internal.com.sun.jdi.VMDisconnectedException;
import consulo.internal.com.sun.jdi.VirtualMachine;
import consulo.internal.com.sun.jdi.event.*;
import consulo.internal.com.sun.jdi.request.EventRequest;
import consulo.internal.com.sun.jdi.request.EventRequestManager;
import consulo.internal.com.sun.jdi.request.ThreadDeathRequest;
import consulo.internal.com.sun.jdi.request.ThreadStartRequest;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * @author lex
 */
public class DebugProcessEvents extends DebugProcessImpl {
    private static final Logger LOG = Logger.getInstance(DebugProcessEvents.class);

    private DebuggerEventThread myEventThread;

    public DebugProcessEvents(Project project) {
        super(project);
        DebuggerSettings.getInstance().addCapturePointsSettingsListener(this::createStackCapturingBreakpoints, myDisposable);
    }

    @Override
    protected void commitVM(VirtualMachine vm) {
        super.commitVM(vm);
        if (vm != null) {
            vmAttached();
            myEventThread = new DebuggerEventThread();
            Application.get().executeOnPooledThread(myEventThread);
        }
    }

    @Nonnull
    public LocalizeValue getEventText(Breakpoint breakpoint, Event event) {
        return switch (event) {
            case LocatableEvent locatableEvent -> {
                try {
                    yield breakpoint != null
                        ? LocalizeValue.ofNullable(breakpoint.getEventMessage(locatableEvent))
                        : JavaDebuggerLocalize.statusGenericBreakpointReached();
                }
                catch (InternalException e) {
                    yield JavaDebuggerLocalize.statusGenericBreakpointReached();
                }
            }
            case VMStartEvent startEvent -> JavaDebuggerLocalize.statusProcessStarted();
            case VMDeathEvent deathEvent -> JavaDebuggerLocalize.statusProcessTerminated();
            case VMDisconnectEvent disconnectEvent -> {
                RemoteConnection connection = getConnection();
                String addressDisplayName = DebuggerUtils.getAddressDisplayName(connection);
                LocalizeValue transportName = DebuggerUtils.getTransportName(connection);
                yield JavaDebuggerLocalize.statusDisconnected(addressDisplayName, transportName);
            }
            default -> LocalizeValue.empty();
        };
    }

    private class DebuggerEventThread implements Runnable {
        private final VirtualMachineProxyImpl myVmProxy;

        DebuggerEventThread() {
            myVmProxy = getVirtualMachineProxy();
        }

        private boolean myIsStopped = false;

        public synchronized void stopListening() {
            myIsStopped = true;
        }

        private synchronized boolean isStopped() {
            return myIsStopped;
        }

        @Override
        public void run() {
            String oldThreadName = Thread.currentThread().getName();
            Thread.currentThread().setName("DebugProcessEvents");

            try {
                EventQueue eventQueue = myVmProxy.eventQueue();
                while (!isStopped()) {
                    try {
                        EventSet eventSet = eventQueue.remove();

                        getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
                            @Override
                            public Priority getPriority() {
                                return Priority.HIGH;
                            }

                            @Override
                            protected void action() throws Exception {
                                int processed = 0;
                                for (Event event : eventSet) {
                                    if (myReturnValueWatcher != null && myReturnValueWatcher.isEnabled()) {
                                        if (myReturnValueWatcher.processEvent(event)) {
                                            processed++;
                                            continue;
                                        }
                                    }
                                    if (event instanceof ThreadStartEvent threadStartEvent) {
                                        processed++;
                                        ThreadReference thread = threadStartEvent.thread();
                                        getVirtualMachineProxy().threadStarted(thread);
                                        myDebugProcessDispatcher.getMulticaster().threadStarted(DebugProcessEvents.this, thread);
                                    }
                                    else if (event instanceof ThreadDeathEvent threadDeathEvent) {
                                        processed++;
                                        ThreadReference thread = threadDeathEvent.thread();
                                        getVirtualMachineProxy().threadStopped(thread);
                                        myDebugProcessDispatcher.getMulticaster().threadStopped(DebugProcessEvents.this, thread);
                                    }
                                }

                                if (processed == eventSet.size()) {
                                    eventSet.resume();
                                    return;
                                }

                                LocatableEvent locatableEvent = getLocatableEvent(eventSet);
                                if (eventSet.suspendPolicy() == EventRequest.SUSPEND_ALL) {
                                    // check if there is already one request with policy SUSPEND_ALL
                                    for (SuspendContextImpl context : getSuspendManager().getEventContexts()) {
                                        if (context.getSuspendPolicy() == EventRequest.SUSPEND_ALL) {
                                            if (isResumeOnlyCurrentThread() && locatableEvent != null && !context.isEvaluating()) {
                                                // if step event is present - switch context
                                                getSuspendManager().resume(context);
                                                //((SuspendManagerImpl)getSuspendManager()).popContext(context);
                                                continue;
                                            }
                                            if (!DebuggerSession.enableBreakpointsDuringEvaluation()) {
                                                notifySkippedBreakpoints(locatableEvent);
                                                eventSet.resume();
                                                return;
                                            }
                                        }
                                    }
                                }

                                SuspendContextImpl suspendContext = null;

                                if (isResumeOnlyCurrentThread() && locatableEvent != null) {
                                    for (SuspendContextImpl context : getSuspendManager().getEventContexts()) {
                                        ThreadReferenceProxyImpl threadProxy =
                                            getVirtualMachineProxy().getThreadReferenceProxy(locatableEvent.thread());
                                        if (context.getSuspendPolicy() == EventRequest.SUSPEND_ALL && context.isExplicitlyResumed(
                                            threadProxy)) {
                                            context.myResumedThreads.remove(threadProxy);
                                            suspendContext = context;
                                            suspendContext.myVotesToVote = eventSet.size();
                                            break;
                                        }
                                    }
                                }

                                if (suspendContext == null) {
                                    suspendContext = getSuspendManager().pushSuspendContext(eventSet);
                                }

                                for (Event event : eventSet) {
                                    //if (LOG.isDebugEnabled()) {
                                    //  LOG.debug("EVENT : " + event);
                                    //}
                                    try {
                                        if (event instanceof VMStartEvent vmStartEvent) {
                                            //Sun WTK fails when J2ME when event set is resumed on VMStartEvent
                                            processVMStartEvent(suspendContext, vmStartEvent);
                                        }
                                        else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                                            processVMDeathEvent(suspendContext, event);
                                        }
                                        else if (event instanceof ClassPrepareEvent classPrepareEvent) {
                                            processClassPrepareEvent(suspendContext, classPrepareEvent);
                                        }
                                        //AccessWatchpointEvent, BreakpointEvent, ExceptionEvent, MethodEntryEvent, MethodExitEvent,
                                        //ModificationWatchpointEvent, StepEvent, WatchpointEvent
                                        else if (event instanceof StepEvent stepEvent) {
                                            processStepEvent(suspendContext, stepEvent);
                                        }
                                        else if (event instanceof LocatableEvent locatableEvent1) {
                                            processLocatableEvent(suspendContext, locatableEvent1);
                                        }
                                        else if (event instanceof ClassUnloadEvent) {
                                            processDefaultEvent(suspendContext);
                                        }
                                    }
                                    catch (VMDisconnectedException e) {
                                        LOG.debug(e);
                                    }
                                    catch (InternalException e) {
                                        LOG.info(e);
                                    }
                                    catch (Throwable e) {
                                        LOG.error(e);
                                    }
                                }
                            }
                        });
                    }
                    catch (InternalException e) {
                        LOG.debug(e);
                    }
                    catch (InterruptedException | ProcessCanceledException | VMDisconnectedException e) {
                        throw e;
                    }
                    catch (Throwable e) {
                        LOG.debug(e);
                    }
                }
            }
            catch (InterruptedException | VMDisconnectedException e) {
                invokeVMDeathEvent();
            }
            finally {
                Thread.interrupted(); // reset interrupted status
                Thread.currentThread().setName(oldThreadName);
            }
        }

        private void invokeVMDeathEvent() {
            getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
                @Override
                protected void action() throws Exception {
                    SuspendContextImpl suspendContext = getSuspendManager().pushSuspendContext(EventRequest.SUSPEND_NONE, 1);
                    processVMDeathEvent(suspendContext, null);
                }
            });
        }
    }

    private static void preprocessEvent(SuspendContextImpl suspendContext, ThreadReference thread) {
        ThreadReferenceProxyImpl oldThread = suspendContext.getThread();
        suspendContext.setThread(thread);

        if (oldThread == null) {
            //this is the first event in the eventSet that we process
            suspendContext.getDebugProcess().beforeSuspend(suspendContext);
        }
    }

    private void processVMStartEvent(SuspendContextImpl suspendContext, VMStartEvent event) {
        preprocessEvent(suspendContext, event.thread());

        LOG.debug("enter: processVMStartEvent()");

        getSuspendManager().voteResume(suspendContext);
    }

    private void vmAttached() {
        DebuggerManagerThreadImpl.assertIsManagerThread();
        LOG.assertTrue(!isAttached());
        if (myState.compareAndSet(State.INITIAL, State.ATTACHED)) {
            VirtualMachineProxyImpl machineProxy = getVirtualMachineProxy();
            EventRequestManager requestManager = machineProxy.eventRequestManager();

            if (machineProxy.canGetMethodReturnValues()) {
                myReturnValueWatcher = new MethodReturnValueWatcher(requestManager);
            }

            ThreadStartRequest threadStartRequest = requestManager.createThreadStartRequest();
            threadStartRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
            threadStartRequest.enable();
            ThreadDeathRequest threadDeathRequest = requestManager.createThreadDeathRequest();
            threadDeathRequest.setSuspendPolicy(EventRequest.SUSPEND_NONE);
            threadDeathRequest.enable();

            // fill position managers
            ((DebuggerManagerImpl)DebuggerManager.getInstance(getProject())).getCustomPositionManagerFactories()
                .map(factory -> factory.apply(this))
                .filter(Objects::nonNull)
                .forEach(this::appendPositionManager);
            PositionManagerFactory.EP_NAME.getExtensionList(getProject())
                .stream()
                .map(factory -> factory.createPositionManager(this))
                .filter(Objects::nonNull)
                .forEach(this::appendPositionManager);

            myDebugProcessDispatcher.getMulticaster().processAttached(this);

            createStackCapturingBreakpoints();

            // breakpoints should be initialized after all processAttached listeners work
            getProject().getApplication().runReadAction(() -> {
                XDebugSession session = getSession().getXDebugSession();
                if (session != null) {
                    session.initBreakpoints();
                }
            });

            LOG.debug("leave: processVMStartEvent()");

            XDebugSession session = getSession().getXDebugSession();
            if (session != null) {
                session.setPauseActionSupported(true);
            }
        }
    }

    private void createStackCapturingBreakpoints() {
        getManagerThread().invoke(new DebuggerCommandImpl() {
            @Override
            public Priority getPriority() {
                return Priority.HIGH;
            }

            @Override
            protected void action() throws Exception {
                StackCapturingLineBreakpoint.recreateAll(DebugProcessEvents.this);
            }
        });
    }

    private void processVMDeathEvent(SuspendContextImpl suspendContext, Event event) {
        try {
            preprocessEvent(suspendContext, null);
            cancelRunToCursorBreakpoint();
        }
        finally {
            if (myEventThread != null) {
                myEventThread.stopListening();
                myEventThread = null;
            }
            closeProcess(false);
        }
    }

    private void processClassPrepareEvent(SuspendContextImpl suspendContext, ClassPrepareEvent event) {
        preprocessEvent(suspendContext, event.thread());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Class prepared: " + event.referenceType().name());
        }
        suspendContext.getDebugProcess().getRequestsManager().processClassPrepared(event);

        getSuspendManager().voteResume(suspendContext);
    }

    private void processStepEvent(SuspendContextImpl suspendContext, StepEvent event) {
        ThreadReference thread = event.thread();
        //LOG.assertTrue(thread.isSuspended());
        preprocessEvent(suspendContext, thread);

        //noinspection HardCodedStringLiteral
        RequestHint hint = (RequestHint)event.request().getProperty("hint");

        deleteStepRequests(event.thread());

        boolean shouldResume = false;

        Project project = getProject();
        if (hint != null) {
            int nextStepDepth = hint.getNextStepDepth(suspendContext);
            if (nextStepDepth == RequestHint.RESUME) {
                getSession().clearSteppingThrough();
                shouldResume = true;
            }
            else if (nextStepDepth != RequestHint.STOP) {
                ThreadReferenceProxyImpl threadProxy = suspendContext.getThread();
                doStep(suspendContext, threadProxy, hint.getSize(), nextStepDepth, hint);
                shouldResume = true;
            }

            if (!shouldResume && hint.isRestoreBreakpoints()) {
                DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().enableBreakpoints(this);
            }
        }

        if (shouldResume) {
            getSuspendManager().voteResume(suspendContext);
        }
        else {
            if (myReturnValueWatcher != null) {
                myReturnValueWatcher.disable();
            }
            getSuspendManager().voteSuspend(suspendContext);
            if (hint != null) {
                MethodFilter methodFilter = hint.getMethodFilter();
                if (methodFilter instanceof NamedMethodFilter namedMethodFilter && !hint.wasStepTargetMethodMatched()) {
                    String message = "Method <b>" + namedMethodFilter.getMethodName() + "()</b> has not been called";
                    XDebuggerUIConstants.NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION).notify(project);
                }
                if (hint.wasStepTargetMethodMatched() && hint.isResetIgnoreFilters()) {
                    checkPositionNotFiltered(suspendContext.getThread(), filters -> mySession.resetIgnoreStepFiltersFlag());
                }
            }
        }
    }

    private void processLocatableEvent(SuspendContextImpl suspendContext, LocatableEvent event) {
        ThreadReference thread = event.thread();
        //LOG.assertTrue(thread.isSuspended());
        preprocessEvent(suspendContext, thread);

        //we use schedule to allow processing other events during processing this one
        //this is especially necessary if a method is breakpoint condition
        getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
            @Override
            public void contextAction() throws Exception {
                SuspendManager suspendManager = getSuspendManager();
                SuspendContextImpl evaluatingContext = SuspendManagerUtil.getEvaluatingContext(suspendManager, suspendContext.getThread());

                if (evaluatingContext != null && !DebuggerSession.enableBreakpointsDuringEvaluation()) {
                    notifySkippedBreakpoints(event);
                    // is inside evaluation, so ignore any breakpoints
                    suspendManager.voteResume(suspendContext);
                    return;
                }

                LocatableEventRequestor requestor = (LocatableEventRequestor)getRequestsManager().findRequestor(event.request());

                boolean resumePreferred = requestor != null && DebuggerSettings.SUSPEND_NONE.equals(requestor.getSuspendPolicy());
                boolean requestHit;
                try {
                    requestHit = (requestor != null) && requestor.processLocatableEvent(this, event);
                }
                catch (LocatableEventRequestor.EventProcessingException ex) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(ex.getMessage());
                    }
                    boolean[] considerRequestHit = new boolean[]{true};
                    DebuggerInvocationUtil.invokeAndWait(
                        getProject(),
                        () -> {
                            String displayName = requestor instanceof Breakpoint breakpoint
                                ? breakpoint.getDisplayName()
                                : requestor.getClass().getSimpleName();
                            LocalizeValue message =
                                JavaDebuggerLocalize.errorEvaluatingBreakpointConditionOrAction(displayName, ex.getMessage());
                            //noinspection RequiredXAction
                            considerRequestHit[0] =
                                Messages.showYesNoDialog(getProject(), message.get(), ex.getTitle(), UIUtil.getQuestionIcon()) == Messages.YES;
                        },
                        getProject().getApplication().getNoneModalityState()
                    );
                    requestHit = considerRequestHit[0];
                    resumePreferred = !requestHit;
                }

                if (requestHit && requestor instanceof Breakpoint requestorBreakpoint) {
                    // if requestor is a breakpoint and this breakpoint was hit, no matter its suspend policy
                    getProject().getApplication().runReadAction(() -> {
                        XDebugSession session = getSession().getXDebugSession();
                        if (session != null) {
                            XBreakpoint breakpoint = requestorBreakpoint.getXBreakpoint();
                            if (breakpoint != null) {
                                session.processDependencies(breakpoint);
                            }
                        }
                    });
                }

                if (!requestHit || resumePreferred) {
                    suspendManager.voteResume(suspendContext);
                }
                else {
                    if (myReturnValueWatcher != null) {
                        myReturnValueWatcher.disable();
                    }
                    //if (suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL) {
                    //  // there could be explicit resume as a result of call to voteSuspend()
                    //  // e.g. when breakpoint was considered invalid, in that case the filter will be applied _after_
                    //  // resuming and all breakpoints in other threads will be ignored.
                    //  // As resume() implicitly cleares the filter, the filter must be always applied _before_ any resume() action happens
                    //  myBreakpointManager.applyThreadFilter(DebugProcessEvents.this, event.thread());
                    //}
                    suspendManager.voteSuspend(suspendContext);
                }
            }
        });
    }

    private void notifySkippedBreakpoints(LocatableEvent event) {
        XDebuggerUIConstants.NOTIFICATION_GROUP.createNotification(
            JavaDebuggerLocalize.messageBreakpointSkipped(event.location()).get(),
            NotificationType.INFORMATION
        ).notify(getProject());
    }

    @Nullable
    private static LocatableEvent getLocatableEvent(EventSet eventSet) {
        return (LocatableEvent)eventSet.stream().filter(event -> event instanceof LocatableEvent).findFirst().orElse(null);
    }

    private void processDefaultEvent(SuspendContextImpl suspendContext) {
        preprocessEvent(suspendContext, null);
        getSuspendManager().voteResume(suspendContext);
    }
}
