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
package com.intellij.java.debugger.impl.memory.action.tracking;

import com.intellij.java.debugger.DebuggerManager;
import com.intellij.java.debugger.impl.engine.DebugProcessImpl;
import com.intellij.java.debugger.impl.memory.action.DebuggerTreeAction;
import com.intellij.java.debugger.impl.memory.component.MemoryViewDebugProcessData;
import com.intellij.java.debugger.impl.memory.ui.StackFramePopup;
import com.intellij.java.debugger.impl.memory.utils.StackFrameItem;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.ide.impl.idea.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class JumpToAllocationSourceAction extends DebuggerTreeAction
{
	@Override
	public void update(AnActionEvent e)
	{
		e.getPresentation().setVisible(getStack(e) != null);
	}

	@Override
	protected void perform(XValueNodeImpl node, @Nonnull String nodeName, AnActionEvent e)
	{
		final Project project = e.getData(Project.KEY);
		final List<StackFrameItem> stack = getStack(e);
		if(project != null && stack != null)
		{
			final XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
			if(session != null)
			{
				DebugProcessImpl process = (DebugProcessImpl) DebuggerManager.getInstance(project).getDebugProcess(session.getDebugProcess().getProcessHandler());
				StackFramePopup.show(stack, process);
			}
		}
	}

	@Nullable
	private List<StackFrameItem> getStack(AnActionEvent e)
	{
		final Project project = e.getData(Project.KEY);
		final XValueNodeImpl selectedNode = getSelectedNode(e.getDataContext());
		final ObjectReference ref = selectedNode != null ? getObjectReference(selectedNode) : null;
		if(project == null || ref == null)
		{
			return null;
		}

		final XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
		if(session != null)
		{
			final MemoryViewDebugProcessData data = DebuggerManager.getInstance(project).getDebugProcess(session.getDebugProcess().getProcessHandler()).getUserData(MemoryViewDebugProcessData.KEY);
			return data != null ? data.getTrackedStacks().getStack(ref) : null;
		}

		return null;
	}
}
