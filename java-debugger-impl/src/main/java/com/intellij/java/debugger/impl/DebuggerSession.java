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
package com.intellij.java.debugger.impl;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.StackFrameContext;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.jdi.StackFrameProxy;
import com.intellij.java.debugger.impl.engine.*;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationListener;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.engine.requests.RequestManagerImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.jdi.ThreadReferenceProxyImpl;
import com.intellij.java.debugger.impl.ui.breakpoints.Breakpoint;
import com.intellij.java.debugger.impl.ui.breakpoints.BreakpointWithHighlighter;
import com.intellij.java.debugger.impl.ui.breakpoints.LineBreakpoint;
import com.intellij.java.execution.configurations.RemoteConnection;
import com.intellij.java.execution.configurations.RemoteState;
import com.intellij.java.language.psi.PsiElementFinder;
import consulo.application.Application;
import consulo.content.bundle.Sdk;
import consulo.disposer.Disposer;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.debug.AbstractDebuggerSession;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.evaluation.ValueLookupManager;
import consulo.execution.debug.ui.XDebuggerUIConstants;
import consulo.execution.unscramble.ThreadState;
import consulo.internal.com.sun.jdi.ObjectCollectedException;
import consulo.internal.com.sun.jdi.ThreadReference;
import consulo.internal.com.sun.jdi.request.EventRequest;
import consulo.internal.com.sun.jdi.request.StepRequest;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.platform.base.localize.ActionLocalize;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.util.Alarm;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.util.lang.TimeoutUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class DebuggerSession implements AbstractDebuggerSession
{
	private static final Logger LOG = Logger.getInstance(DebuggerSession.class);
	// flags
	private final MyDebuggerStateManager myContextManager;

	public enum State
	{
		STOPPED,
		RUNNING,
		WAITING_ATTACH,
		PAUSED,
		WAIT_EVALUATION,
		DISPOSED
	}

	public enum Event
	{
		ATTACHED,
		DETACHED,
		RESUME,
		STEP,
		PAUSE,
		REFRESH,
		CONTEXT,
		START_WAIT_ATTACH,
		DISPOSE,
		REFRESH_WITH_STACK,
		THREADS_REFRESH
	}

	private volatile boolean myIsEvaluating;
	private volatile int myIgnoreFiltersFrameCountThreshold = 0;

	private DebuggerSessionState myState = null;

	private final String mySessionName;
	private final DebugProcessImpl myDebugProcess;
	private final GlobalSearchScope mySearchScope;
	private Sdk myAlternativeJre;
	private Sdk myRunJre;

	private final DebuggerContextImpl SESSION_EMPTY_CONTEXT;
	//Thread, user is currently stepping through
	private final AtomicReference<ThreadReferenceProxyImpl> mySteppingThroughThread = new AtomicReference<>();
	protected final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

	private boolean myModifiedClassesScanRequired = false;

	public boolean isSteppingThrough(ThreadReferenceProxyImpl threadProxy)
	{
		return Comparing.equal(mySteppingThroughThread.get(), threadProxy);
	}

	public void setSteppingThrough(ThreadReferenceProxyImpl threadProxy)
	{
		mySteppingThroughThread.set(threadProxy);
	}

	public void clearSteppingThrough()
	{
		mySteppingThroughThread.set(null);
		resetIgnoreStepFiltersFlag();
	}

	@Nonnull
	public GlobalSearchScope getSearchScope()
	{
		return mySearchScope;
	}

	public Sdk getAlternativeJre()
	{
		return myAlternativeJre;
	}

	public void setAlternativeJre(Sdk sdk)
	{
		myAlternativeJre = sdk;
		PsiElementFinder.EP_NAME.findExtensionOrFail(getProject(), AlternativeJreClassFinder.class).clearCache();
	}

	public Sdk getRunJre()
	{
		return myRunJre;
	}

	public boolean isModifiedClassesScanRequired()
	{
		return myModifiedClassesScanRequired;
	}

	public void setModifiedClassesScanRequired(boolean modifiedClassesScanRequired)
	{
		myModifiedClassesScanRequired = modifiedClassesScanRequired;
	}

	private class MyDebuggerStateManager extends DebuggerStateManager
	{
		private DebuggerContextImpl myDebuggerContext;

		MyDebuggerStateManager()
		{
			myDebuggerContext = SESSION_EMPTY_CONTEXT;
		}

		@Nonnull
		@Override
		public DebuggerContextImpl getContext()
		{
			return myDebuggerContext;
		}

		/**
		 * actually state changes not in the same sequence as you call setState
		 * the 'resuming' setState with context.getSuspendContext() == null may be set prior to
		 * the setState for the context with context.getSuspendContext()
		 * <p>
		 * in this case we assume that the latter setState is ignored
		 * since the thread was resumed
		 */
		@Override
		@RequiredUIAccess
		public void setState(@Nonnull final DebuggerContextImpl context, final State state, final Event event, final String description)
		{
			Application.get().assertIsDispatchThread();
			final DebuggerSession session = context.getDebuggerSession();
			LOG.assertTrue(session == DebuggerSession.this || session == null);
			final Runnable setStateRunnable = () ->
			{
				LOG.assertTrue(myDebuggerContext.isInitialised());
				myDebuggerContext = context;
				if (LOG.isDebugEnabled())
				{
					LOG.debug("DebuggerSession state = " + state + ", event = " + event);
				}

				myIsEvaluating = false;

				myState = new DebuggerSessionState(state, description);
				fireStateChanged(context, event);
			};

			if (context.getSuspendContext() == null)
			{
				setStateRunnable.run();
			}
			else
			{
				getProcess().getManagerThread().schedule(new SuspendContextCommandImpl(context.getSuspendContext())
				{
					@Override
					public PrioritizedTask.Priority getPriority()
					{
						return PrioritizedTask.Priority.HIGH;
					}

					@Override
					public void contextAction() throws Exception
					{
						context.initCaches();
						DebuggerInvocationUtil.swingInvokeLater(getProject(), setStateRunnable);
					}
				});
			}
		}
	}

	static DebuggerSession create(String sessionName, @Nonnull final DebugProcessImpl debugProcess, DebugEnvironment environment) throws ExecutionException
	{
		DebuggerSession session = new DebuggerSession(sessionName, debugProcess, environment);
		try
		{
			session.attach(environment);
		}
		catch (ExecutionException e)
		{
			session.dispose();
			throw e;
		}
		return session;
	}

	private DebuggerSession(String sessionName, @Nonnull final DebugProcessImpl debugProcess, DebugEnvironment environment)
	{
		mySessionName = sessionName;
		myDebugProcess = debugProcess;
		SESSION_EMPTY_CONTEXT = DebuggerContextImpl.createDebuggerContext(this, null, null, null);
		myContextManager = new MyDebuggerStateManager();
		myState = new DebuggerSessionState(State.STOPPED, null);
		myDebugProcess.addDebugProcessListener(new MyDebugProcessListener(debugProcess));
		myDebugProcess.addEvaluationListener(new MyEvaluationListener());
		ValueLookupManager.getInstance(getProject()).startListening();
		mySearchScope = environment.getSearchScope();
		myAlternativeJre = environment.getAlternativeJre();
		myRunJre = environment.getRunJre();
	}

	@Nonnull
	public DebuggerStateManager getContextManager()
	{
		return myContextManager;
	}

	public Project getProject()
	{
		return getProcess().getProject();
	}

	public String getSessionName()
	{
		return mySessionName;
	}

	@Nonnull
	public DebugProcessImpl getProcess()
	{
		return myDebugProcess;
	}

	private static class DebuggerSessionState
	{
		final State myState;
		final String myDescription;

		public DebuggerSessionState(State state, String description)
		{
			myState = state;
			myDescription = description;
		}
	}

	public State getState()
	{
		return myState.myState;
	}

	public String getStateDescription()
	{
		if (myState.myDescription != null)
		{
			return myState.myDescription;
		}

		switch(myState.myState)
		{
			case STOPPED:
				return DebuggerBundle.message("status.debug.stopped");
			case RUNNING:
				return DebuggerBundle.message("status.app.running");
			case WAITING_ATTACH:
				RemoteConnection connection = getProcess().getConnection();
				final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
				final String transportName = DebuggerBundle.getTransportName(connection);
				return connection.isServerMode()
					? DebuggerBundle.message("status.listening", addressDisplayName, transportName)
					: DebuggerBundle.message("status.connecting", addressDisplayName, transportName);
			case PAUSED:
				return DebuggerBundle.message("status.paused");
			case WAIT_EVALUATION:
				return DebuggerBundle.message("status.waiting.evaluation.result");
			case DISPOSED:
				return DebuggerBundle.message("status.debug.stopped");
		}
		return null;
	}

	/* Stepping */
	private void resumeAction(final DebugProcessImpl.ResumeCommand command, Event event)
	{
		getContextManager().setState(SESSION_EMPTY_CONTEXT, State.WAIT_EVALUATION, event, null);
		myDebugProcess.getManagerThread().schedule(command);
	}

	@RequiredUIAccess
	public void stepOut(int stepSize)
	{
		SuspendContextImpl suspendContext = getSuspendContext();
		DebugProcessImpl.ResumeCommand cmd = null;
		for (JvmSteppingCommandProvider handler : JvmSteppingCommandProvider.EP_NAME.getExtensions())
		{
			cmd = handler.getStepOutCommand(suspendContext, stepSize);
			if (cmd != null)
			{
				break;
			}
		}
		if (cmd == null)
		{
			cmd = myDebugProcess.createStepOutCommand(suspendContext, stepSize);
		}
		setSteppingThrough(cmd.getContextThread());
		resumeAction(cmd, Event.STEP);
	}

	@RequiredUIAccess
	public void stepOut()
	{
		stepOut(StepRequest.STEP_LINE);
	}

	@RequiredUIAccess
	public void stepOver(boolean ignoreBreakpoints, int stepSize)
	{
		SuspendContextImpl suspendContext = getSuspendContext();
		DebugProcessImpl.ResumeCommand cmd = null;
		for (JvmSteppingCommandProvider handler : JvmSteppingCommandProvider.EP_NAME.getExtensions())
		{
			cmd = handler.getStepOverCommand(suspendContext, ignoreBreakpoints, stepSize);
			if (cmd != null)
			{
				break;
			}
		}
		if (cmd == null)
		{
			cmd = myDebugProcess.createStepOverCommand(suspendContext, ignoreBreakpoints, stepSize);
		}
		setSteppingThrough(cmd.getContextThread());
		resumeAction(cmd, Event.STEP);
	}

	@RequiredUIAccess
	public void stepOver(boolean ignoreBreakpoints)
	{
		stepOver(ignoreBreakpoints, StepRequest.STEP_LINE);
	}

	@RequiredUIAccess
	public void stepInto(final boolean ignoreFilters, final @Nullable MethodFilter smartStepFilter, int stepSize)
	{
		final SuspendContextImpl suspendContext = getSuspendContext();
		DebugProcessImpl.ResumeCommand cmd = null;
		for (JvmSteppingCommandProvider handler : JvmSteppingCommandProvider.EP_NAME.getExtensions())
		{
			cmd = handler.getStepIntoCommand(suspendContext, ignoreFilters, smartStepFilter, stepSize);
			if (cmd != null)
			{
				break;
			}
		}
		if (cmd == null)
		{
			cmd = myDebugProcess.createStepIntoCommand(suspendContext, ignoreFilters, smartStepFilter, stepSize);
		}
		setSteppingThrough(cmd.getContextThread());
		resumeAction(cmd, Event.STEP);
	}

	@RequiredUIAccess
	public void stepInto(final boolean ignoreFilters, final @Nullable MethodFilter smartStepFilter)
	{
		stepInto(ignoreFilters, smartStepFilter, StepRequest.STEP_LINE);
	}

	@RequiredUIAccess
	public void runToCursor(@Nonnull XSourcePosition position, final boolean ignoreBreakpoints)
	{
		try
		{
			DebugProcessImpl.ResumeCommand runToCursorCommand =
				myDebugProcess.createRunToCursorCommand(getSuspendContext(), position, ignoreBreakpoints);
			setSteppingThrough(runToCursorCommand.getContextThread());
			resumeAction(runToCursorCommand, Event.STEP);
		}
		catch (EvaluateException e)
		{
			Messages.showErrorDialog(e.getMessage(), ActionLocalize.actionRuntocursorText().map(Presentation.NO_MNEMONIC).get());
		}
	}

	@RequiredUIAccess
	public void resume()
	{
		final SuspendContextImpl suspendContext = getSuspendContext();
		if (suspendContext != null)
		{
			clearSteppingThrough();
			resumeAction(myDebugProcess.createResumeCommand(suspendContext), Event.RESUME);
		}
	}

	public void resetIgnoreStepFiltersFlag()
	{
		myIgnoreFiltersFrameCountThreshold = 0;
	}

	public void setIgnoreStepFiltersFlag(int currentStackFrameCount)
	{
		myIgnoreFiltersFrameCountThreshold = myIgnoreFiltersFrameCountThreshold <= 0
			? currentStackFrameCount : Math.min(myIgnoreFiltersFrameCountThreshold, currentStackFrameCount);
	}

	public boolean shouldIgnoreSteppingFilters()
	{
		return myIgnoreFiltersFrameCountThreshold > 0;
	}

	public void pause()
	{
		myDebugProcess.getManagerThread().schedule(myDebugProcess.createPauseCommand());
	}

  /*Presentation*/

	@RequiredUIAccess
	public void showExecutionPoint()
	{
		getContextManager()
			.setState(DebuggerContextUtil.createDebuggerContext(this, getSuspendContext()), State.PAUSED, Event.REFRESH, null);
	}

	@RequiredUIAccess
	public void refresh(final boolean refreshWithStack)
	{
		final State state = getState();
		DebuggerContextImpl context = myContextManager.getContext();
		DebuggerContextImpl newContext =
			DebuggerContextImpl.createDebuggerContext(this, context.getSuspendContext(), context.getThreadProxy(), context.getFrameProxy());
		myContextManager.setState(newContext, state, refreshWithStack ? Event.REFRESH_WITH_STACK : Event.REFRESH, null);
	}

	public void dispose()
	{
		getProcess().dispose();
		Disposer.dispose(myUpdateAlarm);
		DebuggerInvocationUtil.swingInvokeLater(getProject(), () ->
		{
			myContextManager.setState(SESSION_EMPTY_CONTEXT, State.DISPOSED, Event.DISPOSE, null);
			myContextManager.dispose();
		});
	}

	// ManagerCommands
	@Override
	public boolean isStopped()
	{
		return getState() == State.STOPPED;
	}

	public boolean isAttached()
	{
		return !isStopped() && getState() != State.WAITING_ATTACH;
	}

	@Override
	public boolean isPaused()
	{
		return getState() == State.PAUSED;
	}

	public boolean isConnecting()
	{
		return getState() == State.WAITING_ATTACH;
	}

	public boolean isEvaluating()
	{
		return myIsEvaluating;
	}

	public boolean isRunning()
	{
		return getState() == State.RUNNING && !getProcess().getProcessHandler().isProcessTerminated();
	}

	@RequiredUIAccess
	private SuspendContextImpl getSuspendContext()
	{
		Application.get().assertIsDispatchThread();
		return getContextManager().getContext().getSuspendContext();
	}

	@Nullable
	private ExecutionResult attach(DebugEnvironment environment) throws ExecutionException
	{
		RemoteConnection remoteConnection = environment.getRemoteConnection();
		final String addressDisplayName = DebuggerBundle.getAddressDisplayName(remoteConnection);
		final String transportName = DebuggerBundle.getTransportName(remoteConnection);
		final ExecutionResult executionResult = myDebugProcess.attachVirtualMachine(environment, this);
		getContextManager().setState(
			SESSION_EMPTY_CONTEXT,
			State.WAITING_ATTACH,
			Event.START_WAIT_ATTACH,
			DebuggerBundle.message("status.waiting.attach", addressDisplayName, transportName)
		);
		return executionResult;
	}

	private class MyDebugProcessListener extends DebugProcessAdapterImpl
	{
		private final DebugProcessImpl myDebugProcess;

		public MyDebugProcessListener(final DebugProcessImpl debugProcess)
		{
			myDebugProcess = debugProcess;
		}

		//executed in manager thread
		@Override
		public void connectorIsReady()
		{
			DebuggerInvocationUtil.invokeLater(getProject(), () ->
			{
				RemoteConnection connection = myDebugProcess.getConnection();
				final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
				final String transportName = DebuggerBundle.getTransportName(connection);
				final String connectionStatusMessage = connection.isServerMode()
					? DebuggerBundle.message("status.listening", addressDisplayName, transportName)
					: DebuggerBundle.message("status.connecting", addressDisplayName, transportName);
				getContextManager().setState(SESSION_EMPTY_CONTEXT, State.WAITING_ATTACH, Event.START_WAIT_ATTACH, connectionStatusMessage);
			});
		}

		@Override
		public void paused(final SuspendContextImpl suspendContext)
		{
			LOG.debug("paused");

			ThreadReferenceProxyImpl currentThread = suspendContext.getThread();

			if (!shouldSetAsActiveContext(suspendContext))
			{
				DebuggerInvocationUtil.invokeLater(getProject(), () -> getContextManager().fireStateChanged(getContextManager().getContext(), Event.THREADS_REFRESH));
				ThreadReferenceProxyImpl thread = suspendContext.getThread();
				if (thread != null)
				{
					List<Pair<Breakpoint, consulo.internal.com.sun.jdi.event.Event>> descriptors = DebuggerUtilsEx.getEventDescriptors(suspendContext);
					if (!descriptors.isEmpty())
					{
						XDebuggerUIConstants.NOTIFICATION_GROUP.createNotification(
							DebuggerBundle.message("status.breakpoint.reached.in.thread", thread.name()),
							DebuggerBundle.message("status.breakpoint.reached.in.thread.switch"),
							NotificationType.INFORMATION,
							(notification, event) -> {
								if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
								{
									notification.expire();
									getProcess().getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext)
									{
										@Override
										public void contextAction() throws Exception
										{
											final DebuggerContextImpl debuggerContext =
												DebuggerContextUtil.createDebuggerContext(DebuggerSession.this, suspendContext);

											DebuggerInvocationUtil.invokeLater(getProject(), () -> getContextManager()
												.setState(debuggerContext, State.PAUSED, Event.PAUSE, null));
										}
									});
								}
							}
						).notify(getProject());
					}
				}
				if (((SuspendManagerImpl) myDebugProcess.getSuspendManager()).getPausedContexts().size() > 1)
				{
					return;
				}
				else
				{
					currentThread = mySteppingThroughThread.get();
				}
			}
			else
			{
				setSteppingThrough(currentThread);
			}

			final StackFrameContext positionContext;

			if (currentThread == null)
			{
				//Pause pressed
				LOG.assertTrue(suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL);
				SuspendContextImpl oldContext = getProcess().getSuspendManager().getPausedContext();

				if (oldContext != null)
				{
					currentThread = oldContext.getThread();
				}

				if (currentThread == null)
				{
					final Collection<ThreadReferenceProxyImpl> allThreads = getProcess().getVirtualMachineProxy().allThreads();
					// heuristics: try to pre-select EventDispatchThread
					for (final ThreadReferenceProxyImpl thread : allThreads)
					{
						if (ThreadState.isEDT(thread.name()))
						{
							currentThread = thread;
							break;
						}
					}
					if (currentThread == null)
					{
						// heuristics: display the first thread with RUNNABLE status
						for (final ThreadReferenceProxyImpl thread : allThreads)
						{
							currentThread = thread;
							if (currentThread.status() == ThreadReference.THREAD_STATUS_RUNNING)
							{
								break;
							}
						}
					}
				}

				StackFrameProxyImpl proxy = null;
				if (currentThread != null)
				{
					try
					{
						while (!currentThread.isSuspended())
						{
							// wait until thread is considered suspended. Querying data from a thread immediately after VM.suspend()
							// may result in IncompatibleThreadStateException, most likely some time after suspend() VM erroneously thinks that thread is still running
							TimeoutUtil.sleep(10);
						}
						proxy = (currentThread.frameCount() > 0) ? currentThread.frame(0) : null;
					}
					catch (ObjectCollectedException ignored)
					{
						proxy = null;
					}
					catch (EvaluateException e)
					{
						proxy = null;
						LOG.error(e);
					}
				}
				positionContext = new SimpleStackFrameContext(proxy, myDebugProcess);
			}
			else
			{
				positionContext = suspendContext;
			}

			if (currentThread != null)
			{
				try
				{
					final int frameCount = currentThread.frameCount();
					if (frameCount == 0 || (frameCount <= myIgnoreFiltersFrameCountThreshold))
					{
						resetIgnoreStepFiltersFlag();
					}
				}
				catch (EvaluateException e)
				{
					LOG.info(e);
					resetIgnoreStepFiltersFlag();
				}
			}

			SourcePosition position = ContextUtil.getSourcePosition(positionContext);

			if (position != null)
			{
				final List<Pair<Breakpoint, consulo.internal.com.sun.jdi.event.Event>> eventDescriptors = DebuggerUtilsEx.getEventDescriptors(suspendContext);
				final RequestManagerImpl requestsManager = suspendContext.getDebugProcess().getRequestsManager();
				final PsiFile foundFile = position.getFile();
				final boolean sourceMissing = foundFile instanceof PsiCompiledElement;
				for (Pair<Breakpoint, consulo.internal.com.sun.jdi.event.Event> eventDescriptor : eventDescriptors)
				{
					Breakpoint breakpoint = eventDescriptor.getFirst();
					if (breakpoint instanceof LineBreakpoint)
					{
						final SourcePosition breakpointPosition = ((BreakpointWithHighlighter) breakpoint).getSourcePosition();
						if (breakpointPosition == null || (!sourceMissing && breakpointPosition.getLine() != position.getLine()))
						{
							requestsManager.deleteRequest(breakpoint);
							requestsManager.setInvalid(breakpoint, DebuggerBundle.message("error.invalid.breakpoint.source.changed"));
							breakpoint.updateUI();
						}
						else if (sourceMissing)
						{
							// adjust position to be position of the breakpoint in order to show the real originator of the event
							position = breakpointPosition;
							final StackFrameProxy frameProxy = positionContext.getFrameProxy();
							String className;
							try
							{
								className = frameProxy != null ? frameProxy.location().declaringType().name() : "";
							}
							catch (EvaluateException ignored)
							{
								className = "";
							}
							requestsManager.setInvalid(breakpoint, DebuggerBundle.message("error.invalid.breakpoint.source.not.found", className));
							breakpoint.updateUI();
						}
					}
				}
			}

			final DebuggerContextImpl debuggerContext =
				DebuggerContextImpl.createDebuggerContext(DebuggerSession.this, suspendContext, currentThread, null);
			if (suspendContext.getThread() == currentThread)
			{
				debuggerContext.setPositionCache(position);
			}

			DebuggerInvocationUtil.invokeLater(
				getProject(),
				() -> getContextManager().setState(debuggerContext, State.PAUSED, Event.PAUSE, getDescription(debuggerContext))
			);
		}

		private boolean shouldSetAsActiveContext(final SuspendContextImpl suspendContext)
		{
			final ThreadReferenceProxyImpl newThread = suspendContext.getThread();
			if (newThread == null || suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_ALL || isSteppingThrough(newThread))
			{
				return true;
			}
			final SuspendContextImpl currentSuspendContext = getContextManager().getContext().getSuspendContext();
			if (currentSuspendContext == null)
			{
				return mySteppingThroughThread.get() == null;
			}
			if (enableBreakpointsDuringEvaluation())
			{
				final ThreadReferenceProxyImpl currentThread = currentSuspendContext.getThread();
				return currentThread == null || Comparing.equal(currentThread.getThreadReference(), newThread.getThreadReference());
			}
			return false;
		}


		@Override
		public void resumed(SuspendContextImpl suspendContext)
		{
			SuspendContextImpl context = getProcess().getSuspendManager().getPausedContext();
			ThreadReferenceProxyImpl steppingThread = null;
			// single thread stepping
			if (context != null && suspendContext != null && suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD && isSteppingThrough(suspendContext.getThread()))
			{
				steppingThread = suspendContext.getThread();
			}
			final DebuggerContextImpl debuggerContext = context != null ? DebuggerContextImpl.createDebuggerContext(DebuggerSession.this, context, steppingThread != null ? steppingThread : context
					.getThread(), null) : null;

			DebuggerInvocationUtil.invokeLater(getProject(), () ->
			{
				if (debuggerContext != null)
				{
					getContextManager().setState(debuggerContext, State.PAUSED, Event.CONTEXT, getDescription(debuggerContext));
				}
				else
				{
					getContextManager().setState(SESSION_EMPTY_CONTEXT, State.RUNNING, Event.CONTEXT, null);
				}
			});
		}

		@Override
		public void processAttached(final DebugProcessImpl process)
		{
			final RemoteConnection connection = getProcess().getConnection();
			final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
			final String transportName = DebuggerBundle.getTransportName(connection);
			final String message = DebuggerBundle.message("status.connected", addressDisplayName, transportName);

			process.printToConsole(message + "\n");
			DebuggerInvocationUtil.invokeLater(getProject(), () -> getContextManager().setState(SESSION_EMPTY_CONTEXT, State.RUNNING, Event.ATTACHED, message));
		}

		@Override
		public void attachException(final RunProfileState state, final ExecutionException exception, final RemoteConnection remoteConnection)
		{
			DebuggerInvocationUtil.invokeLater(getProject(), () ->
			{
				String message = "";
				if (state instanceof RemoteState)
				{
					message = DebuggerBundle.message(
						"status.connect.failed",
						DebuggerBundle.getAddressDisplayName(remoteConnection),
						DebuggerBundle.getTransportName(remoteConnection)
					);
				}
				message += exception.getMessage();
				getContextManager().setState(SESSION_EMPTY_CONTEXT, State.STOPPED, Event.DETACHED, message);
			});
		}

		@Override
		public void processDetached(final DebugProcessImpl debugProcess, boolean closedByUser)
		{
			if (!closedByUser)
			{
				ProcessHandler processHandler = debugProcess.getProcessHandler();
				if (processHandler != null)
				{
					final RemoteConnection connection = getProcess().getConnection();
					final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
					final String transportName = DebuggerBundle.getTransportName(connection);
					processHandler.notifyTextAvailable(
						DebuggerBundle.message("status.disconnected", addressDisplayName, transportName) + "\n",
						ProcessOutputTypes.SYSTEM
					);
				}
			}
			DebuggerInvocationUtil.invokeLater(getProject(), () ->
			{
				final RemoteConnection connection = getProcess().getConnection();
				final String addressDisplayName = DebuggerBundle.getAddressDisplayName(connection);
				final String transportName = DebuggerBundle.getTransportName(connection);
				getContextManager().setState(
					SESSION_EMPTY_CONTEXT,
					State.STOPPED,
					Event.DETACHED,
					DebuggerBundle.message("status.disconnected", addressDisplayName, transportName)
				);
			});
			clearSteppingThrough();
		}

		@Override
		public void threadStarted(DebugProcess proc, ThreadReference thread)
		{
			notifyThreadsRefresh();
		}

		@Override
		public void threadStopped(DebugProcess proc, ThreadReference thread)
		{
			notifyThreadsRefresh();
			ThreadReferenceProxyImpl steppingThread = mySteppingThroughThread.get();
			if (steppingThread != null && steppingThread.getThreadReference() == thread)
			{
				clearSteppingThrough();
			}
			DebugProcessImpl debugProcess = (DebugProcessImpl) proc;
			if (debugProcess.getRequestsManager().getFilterThread() == thread)
			{
				DebuggerManagerEx.getInstanceEx(proc.getProject()).getBreakpointManager().applyThreadFilter(debugProcess, null);
			}
		}

		private void notifyThreadsRefresh()
		{
			if (!myUpdateAlarm.isDisposed())
			{
				myUpdateAlarm.cancelAllRequests();
				myUpdateAlarm.addRequest(() ->
				{
					final DebuggerStateManager contextManager = getContextManager();
					contextManager.fireStateChanged(contextManager.getContext(), Event.THREADS_REFRESH);
				}, 100, Application.get().getNoneModalityState());
			}
		}
	}

	private static String getDescription(DebuggerContextImpl debuggerContext)
	{
		SuspendContextImpl suspendContext = debuggerContext.getSuspendContext();
		if (suspendContext != null && debuggerContext.getThreadProxy() != suspendContext.getThread())
		{
			return DebuggerBundle.message("status.paused.in.another.thread");
		}
		return null;
	}

	private class MyEvaluationListener implements EvaluationListener
	{
		@Override
		public void evaluationStarted(SuspendContextImpl context)
		{
			myIsEvaluating = true;
		}

		@Override
		public void evaluationFinished(final SuspendContextImpl context)
		{
			myIsEvaluating = false;
			// seems to be not required after move to xdebugger
			//DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
			//  @Override
			//  public void run() {
			//    if (context != getSuspendContext()) {
			//      getContextManager().setState(DebuggerContextUtil.createDebuggerContext(DebuggerSession.this, context), STATE_PAUSED, REFRESH, null);
			//    }
			//  }
			//});
		}
	}

	public static boolean enableBreakpointsDuringEvaluation()
	{
		return false;
	}

	public void sessionResumed()
	{
		XDebugSession session = getXDebugSession();
		if (session != null)
		{
			session.sessionResumed();
		}
	}

	@Nullable
	public XDebugSession getXDebugSession()
	{
		JavaDebugProcess process = myDebugProcess.getXdebugProcess();
		return process != null ? process.getSession() : null;
	}
}
