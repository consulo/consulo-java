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
package com.intellij.java.debugger.impl.memory.ui;

import com.intellij.java.debugger.DebuggerManager;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.JavaDebuggerEditorsProvider;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.JavaValue;
import com.intellij.java.debugger.impl.engine.SuspendContextImpl;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerContextCommandImpl;
import com.intellij.java.debugger.impl.memory.filtering.FilteringResult;
import com.intellij.java.debugger.impl.memory.filtering.FilteringTask;
import com.intellij.java.debugger.impl.memory.filtering.FilteringTaskCallback;
import com.intellij.java.debugger.impl.memory.utils.*;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.MessageDescriptor;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeManagerImpl;
import com.intellij.java.debugger.ui.tree.NodeDescriptor;
import consulo.application.ApplicationManager;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.dataContext.DataContext;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.breakpoint.XExpression;
import consulo.execution.debug.event.XDebugSessionListener;
import consulo.execution.debug.frame.XValue;
import consulo.execution.debug.frame.XValueChildrenList;
import consulo.execution.debug.frame.XValueMarkers;
import consulo.execution.debug.ui.ValueMarkup;
import consulo.ide.impl.idea.xdebugger.impl.XDebugSessionImpl;
import consulo.ide.impl.idea.xdebugger.impl.actions.XDebuggerActionBase;
import consulo.ide.impl.idea.xdebugger.impl.ui.XDebuggerExpressionEditor;
import consulo.ide.impl.idea.xdebugger.impl.ui.tree.XDebuggerTreeState;
import consulo.ide.impl.idea.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import consulo.ide.impl.idea.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.event.AnActionListener;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.event.DoubleClickListener;
import consulo.ui.ex.awt.update.UiNotifyConnector;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class InstancesWindow extends DialogWrapper
{
	private static final Logger LOG = Logger.getInstance(InstancesWindow.class);

	private static final int DEFAULT_WINDOW_WIDTH = 870;
	private static final int DEFAULT_WINDOW_HEIGHT = 400;
	private static final int FILTERING_BUTTON_ADDITIONAL_WIDTH = 30;
	private static final int BORDER_LAYOUT_DEFAULT_GAP = 5;
	private static final int DEFAULT_INSTANCES_LIMIT = 500000;

	private final Project myProject;
	private final DebugProcessImpl myDebugProcess;
	private final InstancesProvider myInstancesProvider;
	private final String myClassName;
	private final MyInstancesView myInstancesView;

	public InstancesWindow(@Nonnull XDebugSession session, @Nonnull InstancesProvider provider, @Nonnull String className)
	{
		super(session.getProject(), false);

		myProject = session.getProject();
		myDebugProcess = (DebugProcessImpl) DebuggerManager.getInstance(myProject).getDebugProcess(session.getDebugProcess().getProcessHandler());
		myInstancesProvider = provider;
		myClassName = className;

		addWarningMessage(null);
		session.addSessionListener(new XDebugSessionListener()
		{
			@Override
			public void sessionStopped()
			{
				ApplicationManager.getApplication().invokeLater(() -> close(OK_EXIT_CODE));
			}
		}, myDisposable);
		setModal(false);
		myInstancesView = new MyInstancesView(session);
		myInstancesView.setPreferredSize(new JBDimension(DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT));

		init();

		JRootPane root = myInstancesView.getRootPane();
		root.setDefaultButton(myInstancesView.myFilterButton);
	}

	private void addWarningMessage(@Nullable String message)
	{
		String warning = message == null ? "" : String.format(". Warning: %s", message);
		setTitle(String.format("Instances of %s%s", myClassName, warning));
	}

	@Nonnull
	@Override
	protected String getDimensionServiceKey()
	{
		return "#org.jetbrains.debugger.memory.view.InstancesWindow";
	}

	@Nullable
	@Override
	protected JComponent createCenterPanel()
	{
		return myInstancesView;
	}

	@Nullable
	@Override
	protected JComponent createSouthPanel()
	{
		JComponent comp = super.createSouthPanel();
		if(comp != null)
		{
			comp.add(myInstancesView.myProgress, BorderLayout.WEST);
		}

		return comp;
	}

	@Nonnull
	@Override
	protected Action[] createActions()
	{
		return new Action[]{new DialogWrapperExitAction("Close", CLOSE_EXIT_CODE)};
	}

	private class MyInstancesView extends JBPanel implements Disposable
	{
		private static final int MAX_TREE_NODE_COUNT = 2000;
		private static final int FILTERING_CHUNK_SIZE = 50;

		private static final int MAX_DURATION_TO_UPDATE_TREE_SECONDS = 3;
		private static final int FILTERING_PROGRESS_UPDATING_MIN_DELAY_MILLIS = 17; // ~ 60 fps

		private final InstancesTree myInstancesTree;
		private final XDebuggerExpressionEditor myFilterConditionEditor;
		private final XDebugSessionListener myDebugSessionListener = new MySessionListener();

		private final MyNodeManager myNodeManager = new MyNodeManager(myProject);

		private final JButton myFilterButton = new JButton("Filter");
		private final FilteringProgressView myProgress = new FilteringProgressView();

		private final Object myFilteringTaskLock = new Object();

		private boolean myIsAndroidVM = false;

		private volatile MyFilteringWorker myFilteringTask = null;

		MyInstancesView(@Nonnull XDebugSession session)
		{
			super(new BorderLayout(0, JBUI.scale(BORDER_LAYOUT_DEFAULT_GAP)));

			Disposer.register(InstancesWindow.this.myDisposable, this);
			final XValueMarkers<?, ?> markers = getValueMarkers(session);
			if(markers != null)
			{
				final MyActionListener listener = new MyActionListener(markers);
				ActionManager.getInstance().addAnActionListener(listener, InstancesWindow.this.myDisposable);
			}
			session.addSessionListener(myDebugSessionListener, InstancesWindow.this.myDisposable);
			final JavaDebuggerEditorsProvider editorsProvider = new JavaDebuggerEditorsProvider();

			myFilterConditionEditor = new ExpressionEditorWithHistory(myProject, myClassName, editorsProvider, InstancesWindow.this.myDisposable);

			myFilterButton.setBorder(BorderFactory.createEmptyBorder());
			final Dimension filteringButtonSize = myFilterConditionEditor.getEditorComponent().getPreferredSize();
			filteringButtonSize.width = JBUI.scale(FILTERING_BUTTON_ADDITIONAL_WIDTH) + myFilterButton.getPreferredSize().width;
			myFilterButton.setPreferredSize(filteringButtonSize);

			final JBPanel filteringPane = new JBPanel(new BorderLayout(JBUI.scale(BORDER_LAYOUT_DEFAULT_GAP), 0));
			final JBLabel sideEffectsWarning = new JBLabel("Warning: filtering may have side effects", SwingConstants.RIGHT);
			sideEffectsWarning.setBorder(JBUI.Borders.empty(1, 0, 0, 0));
			sideEffectsWarning.setComponentStyle(UIUtil.ComponentStyle.SMALL);
			sideEffectsWarning.setFontColor(UIUtil.FontColor.BRIGHTER);

			filteringPane.add(new JBLabel("Condition:"), BorderLayout.WEST);
			filteringPane.add(myFilterConditionEditor.getComponent(), BorderLayout.CENTER);
			filteringPane.add(myFilterButton, BorderLayout.EAST);
			filteringPane.add(sideEffectsWarning, BorderLayout.SOUTH);

			myProgress.addStopActionListener(this::cancelFilteringTask);

			myInstancesTree = new InstancesTree(myProject, editorsProvider, markers, this::updateInstances);

			myFilterButton.addActionListener(e ->
			{
				final String expression = myFilterConditionEditor.getExpression().getExpression();
				if(!expression.isEmpty())
				{
					myFilterConditionEditor.saveTextInHistory();
				}

				myFilterButton.setEnabled(false);
				myInstancesTree.rebuildTree(InstancesTree.RebuildPolicy.RELOAD_INSTANCES);
			});


			final StackFrameList list = new StackFrameList(myDebugProcess);

			list.addListSelectionListener(e -> list.navigateToSelectedValue(false));
			new DoubleClickListener()
			{
				@Override
				protected boolean onDoubleClick(MouseEvent event)
				{
					list.navigateToSelectedValue(true);
					return true;
				}
			}.installOn(list);

			final InstancesWithStackFrameView instancesWithStackFrame = new InstancesWithStackFrameView(session, myInstancesTree, list, myClassName);

			add(filteringPane, BorderLayout.NORTH);
			add(instancesWithStackFrame.getComponent(), BorderLayout.CENTER);

			final JComponent focusedComponent = myFilterConditionEditor.getEditorComponent();
			UiNotifyConnector.doWhenFirstShown(focusedComponent, () -> IdeFocusManager.findInstanceByComponent(focusedComponent).requestFocus(focusedComponent, true));
		}

		@Override
		public void dispose()
		{
			cancelFilteringTask();
			Disposer.dispose(myInstancesTree);
		}

		private void updateInstances()
		{
			cancelFilteringTask();

			myDebugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(myDebugProcess.getDebuggerContext())
			{
				@Override
				public Priority getPriority()
				{
					return Priority.LOWEST;
				}

				@Override
				public void threadAction(@Nonnull SuspendContextImpl suspendContext)
				{
					myIsAndroidVM = AndroidUtil.isAndroidVM(myDebugProcess.getVirtualMachineProxy().getVirtualMachine());
					final int limit = myIsAndroidVM ? AndroidUtil.ANDROID_INSTANCES_LIMIT : DEFAULT_INSTANCES_LIMIT;
					List<ObjectReference> instances = myInstancesProvider.getInstances(limit + 1);

					final EvaluationContextImpl evaluationContext = myDebugProcess.getDebuggerContext().createEvaluationContext();

					if(instances.size() > limit)
					{
						addWarningMessage(String.format("Not all instances will be loaded (only %d)", limit));
						instances = instances.subList(0, limit);
					}

					if(evaluationContext != null)
					{
						synchronized(myFilteringTaskLock)
						{
							myFilteringTask = new MyFilteringWorker(instances, myFilterConditionEditor.getExpression(), evaluationContext);
							myFilteringTask.execute();
						}
					}
				}
			});
		}

		private void cancelFilteringTask()
		{
			if(myFilteringTask != null)
			{
				synchronized(myFilteringTaskLock)
				{
					if(myFilteringTask != null)
					{
						myFilteringTask.cancel();
						myFilteringTask = null;
					}
				}
			}
		}

		private XValueMarkers<?, ?> getValueMarkers(@Nonnull XDebugSession session)
		{
			return session instanceof XDebugSessionImpl ? ((XDebugSessionImpl) session).getValueMarkers() : null;
		}

		private class MySessionListener implements XDebugSessionListener
		{
			private volatile XDebuggerTreeState myTreeState = null;

			@Override
			public void sessionResumed()
			{
				ApplicationManager.getApplication().invokeLater(() ->
				{
					myTreeState = XDebuggerTreeState.saveState(myInstancesTree);
					cancelFilteringTask();

					myInstancesTree.setInfoMessage("The application is running");
				});
			}

			@Override
			public void sessionPaused()
			{
				ApplicationManager.getApplication().invokeLater(() ->
				{
					myProgress.setVisible(true);
					myInstancesTree.rebuildTree(InstancesTree.RebuildPolicy.RELOAD_INSTANCES, myTreeState);
				});
			}
		}

		private class MyActionListener extends AnActionListener.Adapter
		{
			private final XValueMarkers<?, ?> myValueMarkers;

			private MyActionListener(@Nonnull XValueMarkers<?, ?> markers)
			{
				myValueMarkers = markers;
			}

			@Override
			public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event)
			{
				if(dataContext.getData(UIExAWTDataKey.CONTEXT_COMPONENT) == myInstancesTree && (isAddToWatchesAction(action) || isEvaluateExpressionAction(action)))
				{
					XValueNodeImpl selectedNode = XDebuggerTreeActionBase.getSelectedNode(dataContext);

					if(selectedNode != null)
					{
						TreeNode currentNode = selectedNode;
						while(!myInstancesTree.getRoot().equals(currentNode.getParent()))
						{
							currentNode = currentNode.getParent();
						}

						final XValue valueContainer = ((XValueNodeImpl) currentNode).getValueContainer();

						final String expression = valueContainer.getEvaluationExpression();
						if(expression != null)
						{
							myValueMarkers.markValue(valueContainer, new ValueMarkup(expression.replace("@", ""), new JBColor(0, 0), null));
						}

						ApplicationManager.getApplication().invokeLater(() -> myInstancesTree.rebuildTree(InstancesTree.RebuildPolicy.ONLY_UPDATE_LABELS));
					}
				}
			}

			private boolean isAddToWatchesAction(AnAction action)
			{
				final String className = action.getClass().getSimpleName();
				return action instanceof XDebuggerTreeActionBase && className.equals("XAddToWatchesAction");
			}

			private boolean isEvaluateExpressionAction(AnAction action)
			{
				final String className = action.getClass().getSimpleName();
				return action instanceof XDebuggerActionBase && className.equals("EvaluateAction");
			}
		}

		private class MyFilteringCallback implements FilteringTaskCallback
		{
			private final ErrorsValueGroup myErrorsGroup = new ErrorsValueGroup();
			private final EvaluationContextImpl myEvaluationContext;

			private long myFilteringStartedTime;

			private int myProceedCount = 0;
			private int myMatchedCount = 0;
			private int myErrorsCount = 0;

			private long myLastTreeUpdatingTime;
			private long myLastProgressUpdatingTime;

			public MyFilteringCallback(@Nonnull EvaluationContextImpl evaluationContext)
			{
				myEvaluationContext = evaluationContext;
			}

			private XValueChildrenList myChildren = new XValueChildrenList();

			@Override
			public void started(int total)
			{
				myFilteringStartedTime = System.nanoTime();
				myLastTreeUpdatingTime = myFilteringStartedTime;
				myLastProgressUpdatingTime = System.nanoTime();
				ApplicationManager.getApplication().invokeLater(() -> myProgress.start(total));
			}

			@Nonnull
			@Override
			public Action matched(@Nonnull ObjectReference ref)
			{
				final JavaValue val = new InstanceJavaValue(new InstanceValueDescriptor(myProject, ref), myEvaluationContext, myNodeManager);
				myMatchedCount++;
				myProceedCount++;
				myChildren.add(val);
				updateProgress();
				updateTree();

				return myMatchedCount < MAX_TREE_NODE_COUNT ? Action.CONTINUE : Action.STOP;
			}

			@Nonnull
			@Override
			public Action notMatched(@Nonnull ObjectReference ref)
			{
				myProceedCount++;
				updateProgress();

				return Action.CONTINUE;
			}

			@Nonnull
			@Override
			public Action error(@Nonnull ObjectReference ref, @Nonnull String description)
			{
				final JavaValue val = new InstanceJavaValue(new InstanceValueDescriptor(myProject, ref), myEvaluationContext, myNodeManager);
				myErrorsGroup.addErrorValue(description, val);
				myProceedCount++;
				myErrorsCount++;
				updateProgress();
				return Action.CONTINUE;
			}

			@Override
			public void completed(@Nonnull FilteringResult reason)
			{
				if(!myErrorsGroup.isEmpty())
				{
					myChildren.addBottomGroup(myErrorsGroup);
				}

				final long duration = System.nanoTime() - myFilteringStartedTime;
				LOG.info(String.format("Filtering completed in %d ms for %d instances", TimeUnit.NANOSECONDS.toMillis(duration), myProceedCount));

				final int proceed = myProceedCount;
				final int matched = myMatchedCount;
				final int errors = myErrorsCount;
				final XValueChildrenList childrenList = myChildren;
				ApplicationManager.getApplication().invokeLater(() ->
				{
					myProgress.updateProgress(proceed, matched, errors);
					myInstancesTree.addChildren(childrenList, true);
					myFilterButton.setEnabled(true);
					myProgress.complete(reason);
				});
			}

			private void updateProgress()
			{
				final long now = System.nanoTime();
				if(now - myLastProgressUpdatingTime > TimeUnit.MILLISECONDS.toNanos(FILTERING_PROGRESS_UPDATING_MIN_DELAY_MILLIS))
				{
					final int proceed = myProceedCount;
					final int matched = myMatchedCount;
					final int errors = myErrorsCount;
					ApplicationManager.getApplication().invokeLater(() -> myProgress.updateProgress(proceed, matched, errors));
					myLastProgressUpdatingTime = now;
				}
			}

			private void updateTree()
			{
				final long now = System.nanoTime();
				final int newChildrenCount = myChildren.size();
				if(newChildrenCount >= FILTERING_CHUNK_SIZE || (newChildrenCount > 0 && now - myLastTreeUpdatingTime > TimeUnit.SECONDS.toNanos(MAX_DURATION_TO_UPDATE_TREE_SECONDS)))
				{
					final XValueChildrenList children = myChildren;
					ApplicationManager.getApplication().invokeLater(() -> myInstancesTree.addChildren(children, false));
					myChildren = new XValueChildrenList();
					myLastTreeUpdatingTime = System.nanoTime();
				}
			}
		}

		private class MyFilteringWorker extends SwingWorker<Void, Void>
		{
			private final FilteringTask myTask;

			MyFilteringWorker(@Nonnull List<ObjectReference> refs, @Nonnull XExpression expression, @Nonnull EvaluationContextImpl evaluationContext)
			{
				myTask = new FilteringTask(myClassName, myDebugProcess, expression, refs, new MyFilteringCallback(evaluationContext));
			}

			@Override
			protected Void doInBackground() throws Exception
			{
				myTask.run();
				return null;
			}

			public void cancel()
			{
				myTask.cancel();
				super.cancel(false);
			}
		}
	}

	private final static class MyNodeManager extends NodeManagerImpl
	{
		MyNodeManager(Project project)
		{
			super(project, null);
		}

		@Nonnull
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

		@Nonnull
		@Override
		public DebuggerTreeNodeImpl createMessageNode(String message)
		{
			return new DebuggerTreeNodeImpl(null, new MessageDescriptor(message));
		}
	}
}
