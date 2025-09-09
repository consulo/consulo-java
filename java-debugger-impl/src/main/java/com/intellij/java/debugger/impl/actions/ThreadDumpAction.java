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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import com.intellij.java.execution.unscramble.ThreadDumpParser;
import consulo.annotation.component.ActionImpl;
import consulo.execution.debug.XDebugSession;
import consulo.execution.unscramble.ThreadState;
import consulo.internal.com.sun.jdi.*;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.util.collection.SmartList;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 * @author Sascha Weinreuter
 */
@ActionImpl(id = "DumpThreads")
public class ThreadDumpAction extends AnAction {
    public ThreadDumpAction() {
        super(
            JavaDebuggerLocalize.actionThreadDumpText(),
            JavaDebuggerLocalize.actionThreadDumpDescription(),
            PlatformIconGroup.actionsDump()
        );
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();

        DebuggerSession session = context.getDebuggerSession();
        if (session != null && session.isAttached()) {
            DebugProcessImpl process = context.getDebugProcess();
            process.getManagerThread().invoke(new DebuggerCommandImpl() {
                @Override
                protected void action() throws Exception {
                    VirtualMachineProxyImpl vm = process.getVirtualMachineProxy();
                    vm.suspend();
                    try {
                        List<ThreadState> threads = buildThreadStates(vm);
                        project.getApplication().invokeLater(
                            () -> {
                                XDebugSession xSession = session.getXDebugSession();
                                if (xSession != null) {
                                    DebuggerUtilsEx.addThreadDump(project, threads, xSession.getUI(), session);
                                }
                            },
                            project.getApplication().getNoneModalityState()
                        );
                    }
                    finally {
                        vm.resume();
                    }
                }
            });
        }
    }

    static List<ThreadState> buildThreadStates(VirtualMachineProxyImpl vmProxy) {
        List<ThreadReference> threads = vmProxy.getVirtualMachine().allThreads();
        List<ThreadState> result = new ArrayList<>();
        Map<String, ThreadState> nameToThreadMap = new HashMap<>();
        Map<String, String> waitingMap = new HashMap<>(); // key 'waits_for' value
        for (ThreadReference threadReference : threads) {
            StringBuilder buffer = new StringBuilder();
            boolean hasEmptyStack = true;
            int threadStatus = threadReference.status();
            if (threadStatus == ThreadReference.THREAD_STATUS_ZOMBIE) {
                continue;
            }
            String threadName = threadName(threadReference);
            ThreadState threadState = new ThreadState(threadName, threadStatusToState(threadStatus));
            nameToThreadMap.put(threadName, threadState);
            result.add(threadState);
            threadState.setJavaThreadState(threadStatusToJavaThreadState(threadStatus));

            buffer.append("\"").append(threadName).append("\"");
            ReferenceType referenceType = threadReference.referenceType();
            if (referenceType != null) {
                //noinspection HardCodedStringLiteral
                Field daemon = referenceType.fieldByName("daemon");
                if (daemon != null) {
                    Value value = threadReference.getValue(daemon);
                    if (value instanceof BooleanValue booleanValue && booleanValue.booleanValue()) {
                        buffer.append(" ").append(JavaDebuggerLocalize.threadsExportAttributeLabelDaemon());
                        threadState.setDaemon(true);
                    }
                }

                //noinspection HardCodedStringLiteral
                Field priority = referenceType.fieldByName("priority");
                if (priority != null) {
                    Value value = threadReference.getValue(priority);
                    if (value instanceof IntegerValue integerValue) {
                        buffer.append(" ")
                            .append(JavaDebuggerLocalize.threadsExportAttributeLabelPriority(integerValue.intValue()));
                    }
                }

                Field tid = referenceType.fieldByName("tid");
                if (tid != null) {
                    Value value = threadReference.getValue(tid);
                    if (value instanceof LongValue longValue) {
                        buffer.append(" ")
                            .append(JavaDebuggerLocalize.threadsExportAttributeLabelTid(Long.toHexString(longValue.longValue())));
                        buffer.append(" nid=NA");
                    }
                }
            }
            //ThreadGroupReference groupReference = threadReference.threadGroup();
            //if (groupReference != null) {
            //    buffer.append(", ").append(DebuggerBundle.message("threads.export.attribute.label.group", groupReference.name()));
            //}
            String state = threadState.getState();
            if (state != null) {
                buffer.append(" ").append(state);
            }

            buffer.append("\n  java.lang.Thread.State: ").append(threadState.getJavaThreadState());

            try {
                if (vmProxy.canGetOwnedMonitorInfo() && vmProxy.canGetMonitorInfo()) {
                    List<ObjectReference> list = threadReference.ownedMonitors();
                    for (ObjectReference reference : list) {
                        if (!vmProxy.canGetMonitorFrameInfo()) { // java 5 and earlier
                            buffer.append("\n\t ").append(renderLockedObject(reference));
                        }
                        List<ThreadReference> waiting = reference.waitingThreads();
                        for (ThreadReference thread : waiting) {
                            String waitingThreadName = threadName(thread);
                            waitingMap.put(waitingThreadName, threadName);
                            buffer.append("\n\t ")
                                .append(JavaDebuggerLocalize.threadsExportAttributeLabelBlocksThread(waitingThreadName));
                        }
                    }
                }

                ObjectReference waitedMonitor = vmProxy.canGetCurrentContendedMonitor() ? threadReference.currentContendedMonitor() : null;
                if (waitedMonitor != null) {
                    if (vmProxy.canGetMonitorInfo()) {
                        ThreadReference waitedMonitorOwner = waitedMonitor.owningThread();
                        if (waitedMonitorOwner != null) {
                            String monitorOwningThreadName = threadName(waitedMonitorOwner);
                            waitingMap.put(threadName, monitorOwningThreadName);
                            buffer.append("\n\t ").append(JavaDebuggerLocalize.threadsExportAttributeLabelWaitingForThread(
                                monitorOwningThreadName,
                                renderObject(waitedMonitor)
                            ));
                        }
                    }
                }

                List<StackFrame> frames = threadReference.frames();
                hasEmptyStack = frames.size() == 0;

                IntObjectMap<List<ObjectReference>> lockedAt = IntMaps.newIntObjectHashMap();
                if (vmProxy.canGetMonitorFrameInfo()) {
                    for (MonitorInfo info : threadReference.ownedMonitorsAndFrames()) {
                        int stackDepth = info.stackDepth();
                        List<ObjectReference> monitors;
                        if ((monitors = lockedAt.get(stackDepth)) == null) {
                            lockedAt.put(stackDepth, monitors = new SmartList<>());
                        }
                        monitors.add(info.monitor());
                    }
                }

                for (int i = 0, framesSize = frames.size(); i < framesSize; i++) {
                    StackFrame stackFrame = frames.get(i);
                    try {
                        Location location = stackFrame.location();
                        buffer.append("\n\t  ").append(renderLocation(location));

                        List<ObjectReference> monitors = lockedAt.get(i);
                        if (monitors != null) {
                            for (ObjectReference monitor : monitors) {
                                buffer.append("\n\t  - ").append(renderLockedObject(monitor));
                            }
                        }
                    }
                    catch (InvalidStackFrameException e) {
                        buffer.append("\n\t  Invalid stack frame: ").append(e.getMessage());
                    }
                }
            }
            catch (IncompatibleThreadStateException e) {
                buffer.append("\n\t ").append(JavaDebuggerLocalize.threadsExportAttributeErrorIncompatibleState());
            }
            threadState.setStackTrace(buffer.toString(), hasEmptyStack);
            ThreadDumpParser.inferThreadStateDetail(threadState);
        }

        for (String waiting : waitingMap.keySet()) {
            ThreadState waitingThread = nameToThreadMap.get(waiting);
            ThreadState awaitedThread = nameToThreadMap.get(waitingMap.get(waiting));
            awaitedThread.addWaitingThread(waitingThread);
        }

        // detect simple deadlocks
        for (ThreadState thread : result) {
            for (ThreadState awaitingThread : thread.getAwaitingThreads()) {
                if (awaitingThread.isAwaitedBy(thread)) {
                    thread.addDeadlockedThread(awaitingThread);
                    awaitingThread.addDeadlockedThread(thread);
                }
            }
        }

        ThreadDumpParser.sortThreads(result);
        return result;
    }

    @Nonnull
    private static LocalizeValue renderLockedObject(ObjectReference monitor) {
        return JavaDebuggerLocalize.threadsExportAttributeLabelLocked(renderObject(monitor));
    }

    public static String renderObject(ObjectReference monitor) {
        String monitorTypeName;
        try {
            monitorTypeName = monitor.referenceType().name();
        }
        catch (Throwable e) {
            monitorTypeName = "Error getting object type: '" + e.getMessage() + "'";
        }
        return JavaDebuggerLocalize.threadsExportAttributeLabelObjectId(Long.toHexString(monitor.uniqueID()), monitorTypeName).get();
    }

    private static String threadStatusToJavaThreadState(int status) {
        return switch (status) {
            case ThreadReference.THREAD_STATUS_MONITOR -> Thread.State.BLOCKED.name();
            case ThreadReference.THREAD_STATUS_NOT_STARTED -> Thread.State.NEW.name();
            case ThreadReference.THREAD_STATUS_RUNNING -> Thread.State.RUNNABLE.name();
            case ThreadReference.THREAD_STATUS_SLEEPING -> Thread.State.TIMED_WAITING.name();
            case ThreadReference.THREAD_STATUS_WAIT -> Thread.State.WAITING.name();
            case ThreadReference.THREAD_STATUS_ZOMBIE -> Thread.State.TERMINATED.name();
            case ThreadReference.THREAD_STATUS_UNKNOWN -> "unknown";
            default -> "undefined";
        };
    }

    private static String threadStatusToState(int status) {
        return switch (status) {
            case ThreadReference.THREAD_STATUS_MONITOR -> "waiting for monitor entry";
            case ThreadReference.THREAD_STATUS_NOT_STARTED -> "not started";
            case ThreadReference.THREAD_STATUS_RUNNING -> "runnable";
            case ThreadReference.THREAD_STATUS_SLEEPING -> "sleeping";
            case ThreadReference.THREAD_STATUS_WAIT -> "waiting";
            case ThreadReference.THREAD_STATUS_ZOMBIE -> "zombie";
            case ThreadReference.THREAD_STATUS_UNKNOWN -> "unknown";
            default -> "undefined";
        };
    }

    public static String renderLocation(Location location) {
        String sourceName;
        try {
            sourceName = location.sourceName();
        }
        catch (Throwable e) {
            sourceName = "Unknown Source";
        }

        StringBuilder methodName = new StringBuilder();
        try {
            methodName.append(location.declaringType().name());
        }
        catch (Throwable e) {
            methodName.append(e.getMessage());
        }
        methodName.append(".");
        try {
            methodName.append(location.method().name());
        }
        catch (Throwable e) {
            methodName.append(e.getMessage());
        }

        int lineNumber;
        try {
            lineNumber = location.lineNumber();
        }
        catch (Throwable e) {
            lineNumber = -1;
        }
        return JavaDebuggerLocalize.exportThreadsStackframeFormat(methodName.toString(), sourceName, lineNumber).get();
    }

    private static String threadName(ThreadReference threadReference) {
        return threadReference.name() + "@" + threadReference.uniqueID();
    }

    @Override
    @RequiredUIAccess
    public void update(AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        Project project = event.getData(Project.KEY);
        if (project == null) {
            presentation.setEnabled(false);
            return;
        }
        DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
        presentation.setEnabled(debuggerSession != null && debuggerSession.isAttached());
    }
}
