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
package com.intellij.java.debugger.impl.ui.impl;

import com.intellij.java.debugger.impl.*;
import com.intellij.java.debugger.impl.actions.DebuggerAction;
import com.intellij.java.debugger.impl.actions.DebuggerActions;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.engine.events.DebuggerCommandImpl;
import com.intellij.java.debugger.impl.jdi.StackFrameProxyImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTree;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.StackFrameDescriptorImpl;
import consulo.application.Application;
import consulo.application.HelpManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPopupMenu;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.util.Alarm;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ThreadsPanel extends DebuggerTreePanel
{
	@NonNls
	private static final String HELP_ID = "debugging.debugThreads";
	private final Alarm myUpdateLabelsAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
	private static final int LABELS_UPDATE_DELAY_MS = 200;

	public ThreadsPanel(Project project, final DebuggerStateManager stateManager)
	{
		super(project, stateManager);

		final Disposable disposable = DebuggerAction.installEditAction(getThreadsTree(), DebuggerActions.EDIT_FRAME_SOURCE);
		registerDisposable(disposable);

		getThreadsTree().addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER && getThreadsTree().getSelectionCount() == 1)
				{
					DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl) getThreadsTree().getLastSelectedPathComponent();
					if (node != null)
					{
						NodeDescriptorImpl descriptor = node.getDescriptor();
						if (descriptor instanceof StackFrameDescriptorImpl)
						{
							selectFrame(node);
						}
					}
				}
			}
		});
		add(ScrollPaneFactory.createScrollPane(getThreadsTree()), BorderLayout.CENTER);
		stateManager.addListener(new DebuggerContextListener()
		{
			@Override
			public void changeEvent(@Nonnull DebuggerContextImpl newContext, DebuggerSession.Event event)
			{
				if (DebuggerSession.Event.ATTACHED == event || DebuggerSession.Event.RESUME == event)
				{
					startLabelsUpdate();
				}
				else if (DebuggerSession.Event.PAUSE == event || DebuggerSession.Event.DETACHED == event || DebuggerSession.Event.DISPOSE == event)
				{
					myUpdateLabelsAlarm.cancelAllRequests();
				}
				if (DebuggerSession.Event.DETACHED == event || DebuggerSession.Event.DISPOSE == event)
				{
					stateManager.removeListener(this);
				}
			}
		});
		startLabelsUpdate();
	}

	private void startLabelsUpdate()
	{
		if (myUpdateLabelsAlarm.isDisposed())
		{
			return;
		}
		myUpdateLabelsAlarm.cancelAllRequests();
		myUpdateLabelsAlarm.addRequest(new Runnable()
		{
			@Override
			public void run()
			{
				boolean updateScheduled = false;
				try
				{
					if (isUpdateEnabled())
					{
						final ThreadsDebuggerTree tree = getThreadsTree();
						final DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) tree.getModel().getRoot();
						if (root != null)
						{
							final DebugProcessImpl process = getContext().getDebugProcess();
							if (process != null)
							{
								process.getManagerThread().invoke(new DebuggerCommandImpl()
								{
									@Override
									protected void action() throws Exception
									{
										try
										{
											updateNodeLabels(root);
										}
										finally
										{
											reschedule();
										}
									}

									@Override
									protected void commandCancelled()
									{
										reschedule();
									}
								});
								updateScheduled = true;
							}
						}
					}
				}
				finally
				{
					if (!updateScheduled)
					{
						reschedule();
					}
				}
			}

			private void reschedule()
			{
				final DebuggerSession session = getContext().getDebuggerSession();
				if (session != null && session.isAttached() && !session.isPaused() && !myUpdateLabelsAlarm.isDisposed())
				{
					myUpdateLabelsAlarm.addRequest(this, LABELS_UPDATE_DELAY_MS, Application.get().getNoneModalityState());
				}
			}

		}, LABELS_UPDATE_DELAY_MS, Application.get().getNoneModalityState());
	}

	@Override
	public void dispose()
	{
		Disposer.dispose(myUpdateLabelsAlarm);
		super.dispose();
	}

	private static void updateNodeLabels(DebuggerTreeNodeImpl from)
	{
		final int childCount = from.getChildCount();
		for (int idx = 0; idx < childCount; idx++)
		{
			final DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl) from.getChildAt(idx);
			child.getDescriptor().updateRepresentation(null, child::labelChanged);
			updateNodeLabels(child);
		}
	}

	@Override
	protected DebuggerTree createTreeView()
	{
		return new ThreadsDebuggerTree(getProject());
	}

	@Override
	protected ActionPopupMenu createPopupMenu()
	{
		DefaultActionGroup group = (DefaultActionGroup) ActionManager.getInstance().getAction(DebuggerActions.THREADS_PANEL_POPUP);
		return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.THREADS_PANEL_POPUP, group);
	}

	@Override
	public Object getData(Key dataId)
	{
		return HelpManager.HELP_ID == dataId ? HELP_ID : super.getData(dataId);
	}

	private void selectFrame(DebuggerTreeNodeImpl node)
	{
		StackFrameProxyImpl frame = ((StackFrameDescriptorImpl) node.getDescriptor()).getFrameProxy();
		DebuggerContextUtil.setStackFrame(getContextManager(), frame);
	}

	public ThreadsDebuggerTree getThreadsTree()
	{
		return (ThreadsDebuggerTree) getTree();
	}
}
