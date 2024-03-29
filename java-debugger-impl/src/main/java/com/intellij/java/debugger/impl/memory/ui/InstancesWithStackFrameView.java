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
import com.intellij.java.debugger.impl.memory.component.InstancesTracker;
import com.intellij.java.debugger.impl.memory.component.MemoryViewDebugProcessData;
import com.intellij.java.debugger.impl.memory.event.InstancesTrackerListener;
import com.intellij.java.debugger.impl.memory.tracking.TrackingType;
import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import consulo.execution.debug.XDebugSession;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.ActionLink;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.JBSplitter;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

class InstancesWithStackFrameView
{
	private static final float DEFAULT_SPLITTER_PROPORTION = 0.7f;
	private static final String EMPTY_TEXT_WHEN_ITEM_NOT_SELECTED = "Select instance to see stack frame";
	private static final String EMPTY_TEXT_WHEN_STACK_NOT_FOUND = "No stack frame for this instance";
	private static final String TEXT_FOR_ARRAYS = "Arrays could not be tracked";

	private float myHidedProportion;

	private final JBSplitter mySplitter = new JBSplitter(false, DEFAULT_SPLITTER_PROPORTION);
	private boolean myIsHided = false;

	InstancesWithStackFrameView(@Nonnull XDebugSession debugSession, @Nonnull InstancesTree tree, @Nonnull StackFrameList list, @Nonnull String className)
	{
		mySplitter.setFirstComponent(new JBScrollPane(tree));

		final Project project = debugSession.getProject();
		list.setEmptyText(EMPTY_TEXT_WHEN_ITEM_NOT_SELECTED);
		JLabel stackTraceLabel;
		if(isArrayType(className))
		{
			stackTraceLabel = new JBLabel(TEXT_FOR_ARRAYS, SwingConstants.CENTER);
		}
		else
		{
			ActionLink actionLink = new ActionLink("Enable tracking for new instances", PlatformIconGroup.debuggerWatch(), new AnAction()
			{
				@Override
				public void actionPerformed(AnActionEvent e)
				{
					final Project project = e.getData(Project.KEY);
					if(project != null && !project.isDisposed())
					{
						InstancesTracker.getInstance(project).add(className, TrackingType.CREATION);
					}
				}
			});

			actionLink.setHorizontalAlignment(SwingConstants.CENTER);
			actionLink.setPaintUnderline(false);
			stackTraceLabel = actionLink;
		}

		mySplitter.setSplitterProportionKey("InstancesWithStackFrameView.SplitterKey");

		JComponent stackComponent = new JBScrollPane(list);

		if(!project.isDisposed())
		{
			final InstancesTracker tracker = InstancesTracker.getInstance(project);
			tracker.addTrackerListener(new InstancesTrackerListener()
			{
				@Override
				public void classChanged(@Nonnull String name, @Nonnull TrackingType type)
				{
					if(Objects.equals(className, name) && type == TrackingType.CREATION)
					{
						mySplitter.setSecondComponent(stackComponent);
					}
				}

				@Override
				public void classRemoved(@Nonnull String name)
				{
					if(Objects.equals(name, className))
					{
						mySplitter.setSecondComponent(stackTraceLabel);
					}
				}
			}, tree);

			mySplitter.setSecondComponent(tracker.isTracked(className) ? stackComponent : stackTraceLabel);
		}

		mySplitter.setHonorComponentsMinimumSize(false);
		myHidedProportion = DEFAULT_SPLITTER_PROPORTION;

		final MemoryViewDebugProcessData data = DebuggerManager.getInstance(project).getDebugProcess(debugSession.getDebugProcess().getProcessHandler()).getUserData(MemoryViewDebugProcessData.KEY);
		tree.addTreeSelectionListener(e ->
		{
			ObjectReference ref = tree.getSelectedReference();
			if(ref != null && data != null)
			{
				List<StackFrameItem> stack = data.getTrackedStacks().getStack(ref);
				if(stack != null)
				{
					list.setFrameItems(stack);
					if(mySplitter.getProportion() == 1.f)
					{
						mySplitter.setProportion(DEFAULT_SPLITTER_PROPORTION);
					}
					return;
				}
				list.setEmptyText(EMPTY_TEXT_WHEN_STACK_NOT_FOUND);
			}
			else
			{
				list.setEmptyText(EMPTY_TEXT_WHEN_ITEM_NOT_SELECTED);
			}

			list.setFrameItems(Collections.emptyList());
		});
	}

	JComponent getComponent()
	{
		return mySplitter;
	}

	private static boolean isArrayType(@Nonnull String className)
	{
		return className.contains("[]");
	}

	@SuppressWarnings("unused")
	private void hideStackFrame()
	{
		if(!myIsHided)
		{
			myHidedProportion = mySplitter.getProportion();
			mySplitter.getSecondComponent().setVisible(false);
			mySplitter.setProportion(1.f);
			myIsHided = true;
		}
	}

	@SuppressWarnings("unused")
	private void showStackFrame()
	{
		if(myIsHided)
		{
			mySplitter.getSecondComponent().setVisible(true);
			mySplitter.setProportion(myHidedProportion);
			myIsHided = false;
		}
	}
}
