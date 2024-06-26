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

/**
 * class ExportThreadsAction
 * @author Eugene Zhuravlev
 * @author Sascha Weinreuter
 */
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.execution.unscramble.ThreadDumpParser;
import consulo.application.Application;
import consulo.execution.debug.XDebugSession;
import consulo.execution.unscramble.ThreadState;
import consulo.internal.com.sun.jdi.*;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.util.collection.SmartList;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThreadDumpAction extends AnAction implements AnAction.TransparentUpdate
{
	public void actionPerformed(AnActionEvent e)
	{
		final Project project = e.getData(Project.KEY);
		if (project == null)
		{
			return;
		}
		DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();

		final DebuggerSession session = context.getDebuggerSession();
		if (session != null && session.isAttached())
		{
			final DebugProcessImpl process = context.getDebugProcess();
			process.getManagerThread().invoke(new DebuggerCommandImpl()
			{
				protected void action() throws Exception
				{
					final VirtualMachineProxyImpl vm = process.getVirtualMachineProxy();
					vm.suspend();
					try
					{
						final List<ThreadState> threads = buildThreadStates(vm);
						project.getApplication().invokeLater(() -> {
							XDebugSession xSession = session.getXDebugSession();
							if (xSession != null)
							{
								DebuggerUtilsEx.addThreadDump(project, threads, xSession.getUI(), session);
							}
						}, Application.get().getNoneModalityState());
					}
					finally
					{
						vm.resume();
					}
				}
			});
		}
	}

	static List<ThreadState> buildThreadStates(VirtualMachineProxyImpl vmProxy)
	{
		final List<ThreadReference> threads = vmProxy.getVirtualMachine().allThreads();
		final List<ThreadState> result = new ArrayList<>();
		final Map<String, ThreadState> nameToThreadMap = new HashMap<>();
		final Map<String, String> waitingMap = new HashMap<>(); // key 'waits_for' value
		for (ThreadReference threadReference : threads)
		{
			final StringBuilder buffer = new StringBuilder();
			boolean hasEmptyStack = true;
			final int threadStatus = threadReference.status();
			if (threadStatus == ThreadReference.THREAD_STATUS_ZOMBIE)
			{
				continue;
			}
			final String threadName = threadName(threadReference);
			final ThreadState threadState = new ThreadState(threadName, threadStatusToState(threadStatus));
			nameToThreadMap.put(threadName, threadState);
			result.add(threadState);
			threadState.setJavaThreadState(threadStatusToJavaThreadState(threadStatus));

			buffer.append("\"").append(threadName).append("\"");
			ReferenceType referenceType = threadReference.referenceType();
			if (referenceType != null)
			{
				//noinspection HardCodedStringLiteral
				Field daemon = referenceType.fieldByName("daemon");
				if (daemon != null)
				{
					Value value = threadReference.getValue(daemon);
					if (value instanceof BooleanValue booleanValue && booleanValue.booleanValue())
					{
						buffer.append(" ").append(DebuggerBundle.message("threads.export.attribute.label.daemon"));
						threadState.setDaemon(true);
					}
				}

				//noinspection HardCodedStringLiteral
				Field priority = referenceType.fieldByName("priority");
				if (priority != null)
				{
					Value value = threadReference.getValue(priority);
					if (value instanceof IntegerValue integerValue)
					{
						buffer.append(" ").append(DebuggerBundle.message("threads.export.attribute.label.priority", integerValue.intValue()));
					}
				}

				Field tid = referenceType.fieldByName("tid");
				if (tid != null)
				{
					Value value = threadReference.getValue(tid);
					if (value instanceof LongValue longValue)
					{
						buffer.append(" ").append(DebuggerBundle.message("threads.export.attribute.label.tid", Long.toHexString(longValue.longValue())));
						buffer.append(" nid=NA");
					}
				}
			}
			//ThreadGroupReference groupReference = threadReference.threadGroup();
			//if (groupReference != null) {
			//  buffer.append(", ").append(DebuggerBundle.message("threads.export.attribute.label.group", groupReference.name()));
			//}
			final String state = threadState.getState();
			if (state != null)
			{
				buffer.append(" ").append(state);
			}

			buffer.append("\n  java.lang.Thread.State: ").append(threadState.getJavaThreadState());

			try
			{
				if (vmProxy.canGetOwnedMonitorInfo() && vmProxy.canGetMonitorInfo())
				{
					List<ObjectReference> list = threadReference.ownedMonitors();
					for (ObjectReference reference : list)
					{
						if (!vmProxy.canGetMonitorFrameInfo())
						{ // java 5 and earlier
							buffer.append("\n\t ").append(renderLockedObject(reference));
						}
						final List<ThreadReference> waiting = reference.waitingThreads();
						for (ThreadReference thread : waiting)
						{
							final String waitingThreadName = threadName(thread);
							waitingMap.put(waitingThreadName, threadName);
							buffer.append("\n\t ").append(DebuggerBundle.message("threads.export.attribute.label.blocks.thread", waitingThreadName));
						}
					}
				}

				ObjectReference waitedMonitor = vmProxy.canGetCurrentContendedMonitor() ? threadReference.currentContendedMonitor() : null;
				if (waitedMonitor != null)
				{
					if (vmProxy.canGetMonitorInfo())
					{
						ThreadReference waitedMonitorOwner = waitedMonitor.owningThread();
						if (waitedMonitorOwner != null)
						{
							final String monitorOwningThreadName = threadName(waitedMonitorOwner);
							waitingMap.put(threadName, monitorOwningThreadName);
							buffer.append("\n\t ").append(DebuggerBundle.message("threads.export.attribute.label.waiting.for.thread",
									monitorOwningThreadName, renderObject(waitedMonitor)));
						}
					}
				}

				final List<StackFrame> frames = threadReference.frames();
				hasEmptyStack = frames.size() == 0;

				final IntObjectMap<List<ObjectReference>> lockedAt = IntMaps.newIntObjectHashMap();
				if (vmProxy.canGetMonitorFrameInfo())
				{
					for (MonitorInfo info : threadReference.ownedMonitorsAndFrames())
					{
						final int stackDepth = info.stackDepth();
						List<ObjectReference> monitors;
						if ((monitors = lockedAt.get(stackDepth)) == null)
						{
							lockedAt.put(stackDepth, monitors = new SmartList<>());
						}
						monitors.add(info.monitor());
					}
				}

				for (int i = 0, framesSize = frames.size(); i < framesSize; i++)
				{
					final StackFrame stackFrame = frames.get(i);
					try
					{
						final Location location = stackFrame.location();
						buffer.append("\n\t  ").append(renderLocation(location));

						final List<ObjectReference> monitors = lockedAt.get(i);
						if (monitors != null)
						{
							for (ObjectReference monitor : monitors)
							{
								buffer.append("\n\t  - ").append(renderLockedObject(monitor));
							}
						}
					}
					catch (InvalidStackFrameException e)
					{
						buffer.append("\n\t  Invalid stack frame: ").append(e.getMessage());
					}
				}
			}
			catch (IncompatibleThreadStateException e)
			{
				buffer.append("\n\t ").append(DebuggerBundle.message("threads.export.attribute.error.incompatible.state"));
			}
			threadState.setStackTrace(buffer.toString(), hasEmptyStack);
			ThreadDumpParser.inferThreadStateDetail(threadState);
		}

		for (String waiting : waitingMap.keySet())
		{
			final ThreadState waitingThread = nameToThreadMap.get(waiting);
			final ThreadState awaitedThread = nameToThreadMap.get(waitingMap.get(waiting));
			awaitedThread.addWaitingThread(waitingThread);
		}

		// detect simple deadlocks
		for (ThreadState thread : result)
		{
			for (ThreadState awaitingThread : thread.getAwaitingThreads())
			{
				if (awaitingThread.isAwaitedBy(thread))
				{
					thread.addDeadlockedThread(awaitingThread);
					awaitingThread.addDeadlockedThread(thread);
				}
			}
		}

		ThreadDumpParser.sortThreads(result);
		return result;
	}

	private static String renderLockedObject(ObjectReference monitor)
	{
		return DebuggerBundle.message("threads.export.attribute.label.locked", renderObject(monitor));
	}

	public static String renderObject(ObjectReference monitor)
	{
		String monitorTypeName;
		try
		{
			monitorTypeName = monitor.referenceType().name();
		}
		catch (Throwable e)
		{
			monitorTypeName = "Error getting object type: '" + e.getMessage() + "'";
		}
		return DebuggerBundle.message("threads.export.attribute.label.object-id", Long.toHexString(monitor.uniqueID()), monitorTypeName);
	}

	private static String threadStatusToJavaThreadState(int status)
	{
		switch (status)
		{
			case ThreadReference.THREAD_STATUS_MONITOR:
				return Thread.State.BLOCKED.name();
			case ThreadReference.THREAD_STATUS_NOT_STARTED:
				return Thread.State.NEW.name();
			case ThreadReference.THREAD_STATUS_RUNNING:
				return Thread.State.RUNNABLE.name();
			case ThreadReference.THREAD_STATUS_SLEEPING:
				return Thread.State.TIMED_WAITING.name();
			case ThreadReference.THREAD_STATUS_WAIT:
				return Thread.State.WAITING.name();
			case ThreadReference.THREAD_STATUS_ZOMBIE:
				return Thread.State.TERMINATED.name();
			case ThreadReference.THREAD_STATUS_UNKNOWN:
				return "unknown";
			default:
				return "undefined";
		}
	}

	private static String threadStatusToState(int status)
	{
		switch (status)
		{
			case ThreadReference.THREAD_STATUS_MONITOR:
				return "waiting for monitor entry";
			case ThreadReference.THREAD_STATUS_NOT_STARTED:
				return "not started";
			case ThreadReference.THREAD_STATUS_RUNNING:
				return "runnable";
			case ThreadReference.THREAD_STATUS_SLEEPING:
				return "sleeping";
			case ThreadReference.THREAD_STATUS_WAIT:
				return "waiting";
			case ThreadReference.THREAD_STATUS_ZOMBIE:
				return "zombie";
			case ThreadReference.THREAD_STATUS_UNKNOWN:
				return "unknown";
			default:
				return "undefined";
		}
	}

	public static String renderLocation(final Location location)
	{
		String sourceName;
		try
		{
			sourceName = location.sourceName();
		}
		catch (Throwable e)
		{
			sourceName = "Unknown Source";
		}

		final StringBuilder methodName = new StringBuilder();
		try
		{
			methodName.append(location.declaringType().name());
		}
		catch (Throwable e)
		{
			methodName.append(e.getMessage());
		}
		methodName.append(".");
		try
		{
			methodName.append(location.method().name());
		}
		catch (Throwable e)
		{
			methodName.append(e.getMessage());
		}

		int lineNumber;
		try
		{
			lineNumber = location.lineNumber();
		}
		catch (Throwable e)
		{
			lineNumber = -1;
		}
		return DebuggerBundle.message("export.threads.stackframe.format", methodName.toString(), sourceName, lineNumber);
	}

	private static String threadName(ThreadReference threadReference)
	{
		return threadReference.name() + "@" + threadReference.uniqueID();
	}

	public void update(AnActionEvent event)
	{
		Presentation presentation = event.getPresentation();
		Project project = event.getData(Project.KEY);
		if (project == null)
		{
			presentation.setEnabled(false);
			return;
		}
		DebuggerSession debuggerSession = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
		presentation.setEnabled(debuggerSession != null && debuggerSession.isAttached());
	}
}
