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

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.*;
import com.intellij.java.debugger.impl.actions.DebuggerActions;
import com.intellij.java.debugger.impl.actions.JvmSmartStepIntoActionHandler;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.memory.component.InstancesTracker;
import com.intellij.java.debugger.impl.memory.component.MemoryViewDebugProcessData;
import com.intellij.java.debugger.impl.memory.component.MemoryViewManager;
import com.intellij.java.debugger.impl.memory.ui.ClassesFilteredView;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.ui.AlternativeSourceNotificationProvider;
import com.intellij.java.debugger.impl.ui.breakpoints.Breakpoint;
import com.intellij.java.debugger.impl.ui.impl.ThreadsPanel;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.MessageDescriptor;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeManagerImpl;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import consulo.application.AllIcons;
import consulo.execution.debug.*;
import consulo.execution.debug.breakpoint.XBreakpoint;
import consulo.execution.debug.breakpoint.XBreakpointHandler;
import consulo.execution.debug.evaluation.XDebuggerEditorsProvider;
import consulo.execution.debug.event.XDebugSessionListener;
import consulo.execution.debug.frame.XStackFrame;
import consulo.execution.debug.frame.XValueMarkerProvider;
import consulo.execution.debug.step.XSmartStepIntoHandler;
import consulo.execution.debug.ui.DebuggerContentInfo;
import consulo.execution.debug.ui.XDebugTabLayouter;
import consulo.execution.ui.ExecutionConsole;
import consulo.execution.ui.ExecutionConsoleEx;
import consulo.execution.ui.layout.PlaceInGrid;
import consulo.execution.ui.layout.RunnerLayoutUi;
import consulo.fileEditor.EditorNotifications;
import consulo.ide.impl.idea.xdebugger.impl.XDebugSessionImpl;
import consulo.internal.com.sun.jdi.event.Event;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.*;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.event.ContentManagerAdapter;
import consulo.ui.ex.content.event.ContentManagerEvent;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author egor
 */
public class JavaDebugProcess extends XDebugProcess
{
	private final DebuggerSession myJavaSession;
	private final JavaDebuggerEditorsProvider myEditorsProvider;
	private final XBreakpointHandler<?>[] myBreakpointHandlers;
	private final JvmSmartStepIntoActionHandler mySmartStepIntoActionHandler;
	private final NodeManagerImpl myNodeManager;

	public static JavaDebugProcess create(@Nonnull final XDebugSession session, final DebuggerSession javaSession)
	{
		JavaDebugProcess res = new JavaDebugProcess(session, javaSession);
		javaSession.getProcess().setXDebugProcess(res);
		return res;
	}

	protected JavaDebugProcess(@Nonnull final XDebugSession session, final DebuggerSession javaSession)
	{
		super(session);
		myJavaSession = javaSession;
		myEditorsProvider = new JavaDebuggerEditorsProvider();
		final DebugProcessImpl process = javaSession.getProcess();

		List<XBreakpointHandler> handlers = new ArrayList<XBreakpointHandler>();
		handlers.add(new JavaBreakpointHandler.JavaLineBreakpointHandler(process));
		handlers.add(new JavaBreakpointHandler.JavaExceptionBreakpointHandler(process));
		handlers.add(new JavaBreakpointHandler.JavaFieldBreakpointHandler(process));
		handlers.add(new JavaBreakpointHandler.JavaMethodBreakpointHandler(process));
		handlers.add(new JavaBreakpointHandler.JavaWildcardBreakpointHandler(process));

		mySmartStepIntoActionHandler = new JvmSmartStepIntoActionHandler(myJavaSession);

		for(JavaBreakpointHandlerFactory factory : JavaBreakpointHandlerFactory.EP_NAME.getExtensionList())
		{
			handlers.add(factory.createHandler(process));
		}

		myBreakpointHandlers = handlers.toArray(new XBreakpointHandler[handlers.size()]);

		myJavaSession.getContextManager().addListener(new DebuggerContextListener()
		{
			@Override
			public void changeEvent(@Nonnull final DebuggerContextImpl newContext, DebuggerSession.Event event)
			{
				if(event == DebuggerSession.Event.PAUSE || event == DebuggerSession.Event.CONTEXT || event == DebuggerSession.Event.REFRESH && myJavaSession.isPaused())
				{
					final SuspendContextImpl newSuspendContext = newContext.getSuspendContext();
					if(newSuspendContext != null && shouldApplyContext(newContext))
					{
						process.getManagerThread().schedule(new SuspendContextCommandImpl(newSuspendContext)
						{
							@Override
							public void contextAction() throws Exception
							{
								newSuspendContext.initExecutionStacks(newContext.getThreadProxy());

								List<Pair<Breakpoint, Event>> descriptors = DebuggerUtilsEx.getEventDescriptors(newSuspendContext);
								if(!descriptors.isEmpty())
								{
									Breakpoint breakpoint = descriptors.get(0).getFirst();
									XBreakpoint xBreakpoint = breakpoint.getXBreakpoint();
									if(xBreakpoint != null)
									{
										((XDebugSessionImpl) getSession()).breakpointReachedNoProcessing(xBreakpoint, newSuspendContext);
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
				else if(event == DebuggerSession.Event.ATTACHED)
				{
					getSession().rebuildViews(); // to refresh variables views message
				}
			}
		});

		myNodeManager = new NodeManagerImpl(session.getProject(), null)
		{
			@Override
			public DebuggerTreeNodeImpl createNode(final NodeDescriptor descriptor, EvaluationContext evaluationContext)
			{
				return new DebuggerTreeNodeImpl(null, descriptor);
			}

			@Override
			public DebuggerTreeNodeImpl createMessageNode(MessageDescriptor descriptor)
			{
				return new DebuggerTreeNodeImpl(null, descriptor);
			}

			@Override
			public DebuggerTreeNodeImpl createMessageNode(String message)
			{
				return new DebuggerTreeNodeImpl(null, new MessageDescriptor(message));
			}
		};
		session.addSessionListener(new XDebugSessionListener()
		{
			@Override
			public void sessionPaused()
			{
				saveNodeHistory();
				showAlternativeNotification(session.getCurrentStackFrame());
			}

			@Override
			public void stackFrameChanged()
			{
				XStackFrame frame = session.getCurrentStackFrame();
				if(frame instanceof JavaStackFrame)
				{
					showAlternativeNotification(frame);
					StackFrameProxyImpl frameProxy = ((JavaStackFrame) frame).getStackFrameProxy();
					DebuggerContextUtil.setStackFrame(javaSession.getContextManager(), frameProxy);
					saveNodeHistory(frameProxy);
				}
			}

			private void showAlternativeNotification(@Nullable XStackFrame frame)
			{
				if(frame != null)
				{
					XSourcePosition position = frame.getSourcePosition();
					if(position != null)
					{
						VirtualFile file = position.getFile();
						if(!AlternativeSourceNotificationProvider.fileProcessed(file))
						{
							EditorNotifications.getInstance(session.getProject()).updateNotifications(file);
						}
					}
				}
			}
		});
	}

	private void unsetPausedIfNeeded(DebuggerContextImpl context)
	{
		SuspendContextImpl suspendContext = context.getSuspendContext();
		if(suspendContext != null && !suspendContext.suspends(context.getThreadProxy()))
		{
			((XDebugSessionImpl) getSession()).unsetPaused();
		}
	}

	private boolean shouldApplyContext(DebuggerContextImpl context)
	{
		SuspendContextImpl suspendContext = context.getSuspendContext();
		SuspendContextImpl currentContext = (SuspendContextImpl) getSession().getSuspendContext();
		if(suspendContext != null && !suspendContext.equals(currentContext))
		{
			return true;
		}
		JavaExecutionStack currentExecutionStack = currentContext != null ? currentContext.getActiveExecutionStack() : null;
		return currentExecutionStack == null || !Comparing.equal(context.getThreadProxy(), currentExecutionStack.getThreadProxy());
	}

	public void saveNodeHistory()
	{
		saveNodeHistory(getDebuggerStateManager().getContext().getFrameProxy());
	}

	private void saveNodeHistory(final StackFrameProxyImpl frameProxy)
	{
		myJavaSession.getProcess().getManagerThread().invoke(new DebuggerCommandImpl()
		{
			@Override
			protected void action() throws Exception
			{
				myNodeManager.setHistoryByContext(frameProxy);
			}

			@Override
			public Priority getPriority()
			{
				return Priority.NORMAL;
			}
		});
	}

	private DebuggerStateManager getDebuggerStateManager()
	{
		return myJavaSession.getContextManager();
	}

	public DebuggerSession getDebuggerSession()
	{
		return myJavaSession;
	}

	@Nonnull
	@Override
	public XDebuggerEditorsProvider getEditorsProvider()
	{
		return myEditorsProvider;
	}

	@Override
	public void startStepOver()
	{
		myJavaSession.stepOver(false);
	}

	@Override
	public void startStepInto()
	{
		myJavaSession.stepInto(false, null);
	}

	@Override
	public void startForceStepInto()
	{
		myJavaSession.stepInto(true, null);
	}

	@Override
	public void startStepOut()
	{
		myJavaSession.stepOut();
	}

	@Override
	public void stop()
	{
		myJavaSession.dispose();
		myNodeManager.dispose();
	}

	@Override
	public void startPausing()
	{
		myJavaSession.pause();
	}

	@Override
	public void resume()
	{
		myJavaSession.resume();
	}

	@Override
	public void runToPosition(@Nonnull XSourcePosition position)
	{
		myJavaSession.runToCursor(position, false);
	}

	@Nonnull
	@Override
	public XBreakpointHandler<?>[] getBreakpointHandlers()
	{
		return myBreakpointHandlers;
	}

	@Override
	public boolean checkCanInitBreakpoints()
	{
		return false;
	}

	@Nullable
	@Override
	protected ProcessHandler doGetProcessHandler()
	{
		return myJavaSession.getProcess().getProcessHandler();
	}

	@Nonnull
	@Override
	public ExecutionConsole createConsole()
	{
		ExecutionConsole console = myJavaSession.getProcess().getExecutionResult().getExecutionConsole();
		if(console != null)
		{
			return console;
		}
		return super.createConsole();
	}

	@Nonnull
	@Override
	public XDebugTabLayouter createTabLayouter()
	{
		return new XDebugTabLayouter()
		{
			@Override
			public void registerAdditionalContent(@Nonnull RunnerLayoutUi ui)
			{
				registerThreadsPanel(ui);
				registerMemoryViewPanel(ui);
			}

			@Nonnull
			@Override
			public Content registerConsoleContent(@Nonnull RunnerLayoutUi ui, @Nonnull ExecutionConsole console)
			{
				Content content = null;
				if(console instanceof ExecutionConsoleEx)
				{
					((ExecutionConsoleEx) console).buildUi(ui);
					content = ui.findContent(DebuggerContentInfo.CONSOLE_CONTENT);
				}
				if(content == null)
				{
					content = super.registerConsoleContent(ui, console);
				}
				return content;
			}

			private void registerThreadsPanel(@Nonnull RunnerLayoutUi ui)
			{
				final ThreadsPanel panel = new ThreadsPanel(myJavaSession.getProject(), getDebuggerStateManager());
				final Content threadsContent = ui.createContent(DebuggerContentInfo.THREADS_CONTENT, panel, XDebuggerBundle.message("debugger.session.tab.threads.title"), AllIcons.Debugger.Threads,
						null);
				threadsContent.setCloseable(false);
				ui.addContent(threadsContent, 0, PlaceInGrid.left, true);
				ui.addListener(new ContentManagerAdapter()
				{
					@Override
					public void selectionChanged(ContentManagerEvent event)
					{
						if(event.getContent() == threadsContent)
						{
							if(threadsContent.isSelected())
							{
								panel.setUpdateEnabled(true);
								if(panel.isRefreshNeeded())
								{
									panel.rebuildIfVisible(DebuggerSession.Event.CONTEXT);
								}
							}
							else
							{
								panel.setUpdateEnabled(false);
							}
						}
					}
				}, threadsContent);
			}

			private void registerMemoryViewPanel(@Nonnull RunnerLayoutUi ui)
			{
				final XDebugSession session = getSession();
				final DebugProcessImpl process = myJavaSession.getProcess();
				final InstancesTracker tracker = InstancesTracker.getInstance(myJavaSession.getProject());

				final ClassesFilteredView classesFilteredView = new ClassesFilteredView(session, process, tracker);

				final Content memoryViewContent = ui.createContent(MemoryViewManager.MEMORY_VIEW_CONTENT, classesFilteredView, "Memory View", AllIcons.Debugger.MemoryView.Active, null);

				memoryViewContent.setCloseable(false);
				memoryViewContent.setShouldDisposeContent(true);

				final MemoryViewDebugProcessData data = new MemoryViewDebugProcessData();
				process.putUserData(MemoryViewDebugProcessData.KEY, data);
				session.addSessionListener(new XDebugSessionListener()
				{
					@Override
					public void sessionStopped()
					{
						session.removeSessionListener(this);
						data.getTrackedStacks().clear();
					}
				});

				ui.addContent(memoryViewContent, 0, PlaceInGrid.right, true);
				final DebuggerManagerThreadImpl managerThread = process.getManagerThread();
				ui.addListener(new ContentManagerAdapter()
				{
					@Override
					public void selectionChanged(ContentManagerEvent event)
					{
						if(event != null && event.getContent() == memoryViewContent)
						{
							classesFilteredView.setActive(memoryViewContent.isSelected(), managerThread);
						}
					}
				}, memoryViewContent);
			}
		};
	}

	@Override
	public void registerAdditionalActions(@Nonnull DefaultActionGroup leftToolbar, @Nonnull DefaultActionGroup topToolbar, @Nonnull DefaultActionGroup settings)
	{
		Constraints beforeRunner = new Constraints(Anchor.BEFORE, "Runner.Layout");
		leftToolbar.add(AnSeparator.getInstance(), beforeRunner);
		leftToolbar.add(ActionManager.getInstance().getAction(DebuggerActions.DUMP_THREADS), beforeRunner);
		leftToolbar.add(AnSeparator.getInstance(), beforeRunner);

		Constraints beforeSort = new Constraints(Anchor.BEFORE, "XDebugger.ToggleSortValues");
		settings.addAction(new WatchLastMethodReturnValueAction(), beforeSort);
		settings.addAction(new AutoVarsSwitchAction(), beforeSort);
	}

	private static class AutoVarsSwitchAction extends ToggleAction
	{
		private volatile boolean myAutoModeEnabled;

		public AutoVarsSwitchAction()
		{
			super(DebuggerBundle.message("action.auto.variables.mode"), DebuggerBundle.message("action.auto.variables.mode.description"), null);
			myAutoModeEnabled = DebuggerSettings.getInstance().AUTO_VARIABLES_MODE;
		}

		@Override
		public boolean isSelected(AnActionEvent e)
		{
			return myAutoModeEnabled;
		}

		@Override
		public void setSelected(AnActionEvent e, boolean enabled)
		{
			myAutoModeEnabled = enabled;
			DebuggerSettings.getInstance().AUTO_VARIABLES_MODE = enabled;
			XDebuggerUtil.getInstance().rebuildAllSessionsViews(e.getData(Project.KEY));
		}
	}

	private static class WatchLastMethodReturnValueAction extends ToggleAction
	{
		private volatile boolean myWatchesReturnValues;
		private final String myText;
		private final String myTextUnavailable;

		public WatchLastMethodReturnValueAction()
		{
			super("", DebuggerBundle.message("action.watch.method.return.value.description"), null);
			myWatchesReturnValues = DebuggerSettings.getInstance().WATCH_RETURN_VALUES;
			myText = DebuggerBundle.message("action.watches.method.return.value.enable");
			myTextUnavailable = DebuggerBundle.message("action.watches.method.return.value.unavailable.reason");
		}

		@RequiredUIAccess
		@Override
		public void update(@Nonnull final AnActionEvent e)
		{
			super.update(e);
			final Presentation presentation = e.getPresentation();
			DebugProcessImpl process = getCurrentDebugProcess(e.getData(Project.KEY));
			if(process == null || process.canGetMethodReturnValue())
			{
				presentation.setEnabled(true);
				presentation.setText(myText);
			}
			else
			{
				presentation.setEnabled(false);
				presentation.setText(myTextUnavailable);
			}
		}

		@Override
		public boolean isSelected(AnActionEvent e)
		{
			return myWatchesReturnValues;
		}

		@Override
		public void setSelected(AnActionEvent e, boolean watch)
		{
			myWatchesReturnValues = watch;
			DebuggerSettings.getInstance().WATCH_RETURN_VALUES = watch;
			DebugProcessImpl process = getCurrentDebugProcess(e.getData(Project.KEY));
			if(process != null)
			{
				process.setWatchMethodReturnValuesEnabled(watch);
			}
		}
	}

	@Nullable
	private static DebugProcessImpl getCurrentDebugProcess(@Nullable Project project)
	{
		if(project != null)
		{
			XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
			if(session != null)
			{
				XDebugProcess process = session.getDebugProcess();
				if(process instanceof JavaDebugProcess)
				{
					return ((JavaDebugProcess) process).getDebuggerSession().getProcess();
				}
			}
		}
		return null;
	}

	public NodeManagerImpl getNodeManager()
	{
		return myNodeManager;
	}

	@Override
	public String getCurrentStateMessage()
	{
		String description = myJavaSession.getStateDescription();
		return description != null ? description : super.getCurrentStateMessage();
	}

	@Nullable
	@Override
	public XValueMarkerProvider<?, ?> createValueMarkerProvider()
	{
		return new JavaValueMarker();
	}

	@Nullable
	@Override
	public XSmartStepIntoHandler<?> getSmartStepIntoHandler()
	{
		return mySmartStepIntoActionHandler;
	}
}
