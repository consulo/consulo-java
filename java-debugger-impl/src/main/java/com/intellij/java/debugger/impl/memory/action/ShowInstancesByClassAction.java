/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.memory.action;

import com.intellij.java.debugger.impl.memory.ui.InstancesWindow;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.ide.impl.idea.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import consulo.internal.com.sun.jdi.ObjectReference;
import consulo.internal.com.sun.jdi.ReferenceType;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class ShowInstancesByClassAction extends DebuggerTreeAction
{
	@Override
	protected boolean isEnabled(@Nonnull XValueNodeImpl node, @Nonnull AnActionEvent e)
	{
		final ObjectReference ref = getObjectReference(node);
		final boolean enabled = ref != null && ref.virtualMachine().canGetInstanceInfo();
		if(enabled)
		{
			final String text = String.format("Show %s Objects...", StringUtil.getShortName(ref.referenceType().name()));
			e.getPresentation().setText(text);
		}

		return enabled;
	}

	@Override
	protected void perform(XValueNodeImpl node, @Nonnull String nodeName, AnActionEvent e)
	{
		Project project = e.getData(Project.KEY);
		if(project != null)
		{
			final XDebugSession debugSession = XDebuggerManager.getInstance(project).getCurrentSession();
			final ObjectReference ref = getObjectReference(node);
			if(debugSession != null && ref != null)
			{
				final ReferenceType referenceType = ref.referenceType();
				new InstancesWindow(debugSession, l ->
				{
					final List<ObjectReference> instances = referenceType.instances(l);
					return instances == null ? Collections.emptyList() : instances;
				}, referenceType.name()).show();
			}
		}
	}
}
