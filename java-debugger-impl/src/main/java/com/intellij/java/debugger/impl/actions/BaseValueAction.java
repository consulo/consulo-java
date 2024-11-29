/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.DebuggerContextImpl;
import com.intellij.java.debugger.impl.DebuggerInvocationUtil;
import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.events.SuspendContextCommandImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.java.debugger.impl.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.java.debugger.impl.ui.tree.ValueDescriptor;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.openapi.progress.util.ProgressWindow;
import consulo.internal.com.sun.jdi.Value;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import jakarta.annotation.Nullable;

/*
 * @author Jeka
 */
public abstract class BaseValueAction extends DebuggerAction
{

	public void actionPerformed(AnActionEvent e)
	{
		final DataContext actionContext = e.getDataContext();
		final DebuggerTreeNodeImpl node = getSelectedNode(actionContext);
		final Value value = getValue(node);
		if (value == null)
		{
			return;
		}
		final Project project = e.getData(Project.KEY);
		final DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
		if (debuggerManager == null)
		{
			return;
		}
		final DebuggerContextImpl debuggerContext = debuggerManager.getContext();
		if (debuggerContext == null || debuggerContext.getDebuggerSession() == null)
		{
			return;
		}

		final ProgressWindow progressWindow = new ProgressWindow(true, project);
		SuspendContextCommandImpl getTextCommand = new SuspendContextCommandImpl(debuggerContext.getSuspendContext())
		{
			public Priority getPriority()
			{
				return Priority.HIGH;
			}

			public void contextAction() throws Exception
			{
				//noinspection HardCodedStringLiteral
				progressWindow.setText(DebuggerBundle.message("progress.evaluating", "toString()"));

				final String valueAsString = DebuggerUtilsEx.getValueOrErrorAsString(debuggerContext.createEvaluationContext(), value);

				if (progressWindow.isCanceled())
				{
					return;
				}

				DebuggerInvocationUtil.swingInvokeLater(project, () -> {
					String text = valueAsString;
					if (text == null)
					{
						text = "";
					}
					processText(project, text, node, debuggerContext);
				});
			}
		};
		progressWindow.setTitle(DebuggerBundle.message("title.evaluating"));
		debuggerContext.getDebugProcess().getManagerThread().startProgress(getTextCommand, progressWindow);
	}

	protected abstract void processText(final Project project, String text, DebuggerTreeNodeImpl node, DebuggerContextImpl debuggerContext);

	public void update(AnActionEvent e)
	{
		Presentation presentation = e.getPresentation();
		Value value = getValue(getSelectedNode(e.getDataContext()));
		presentation.setEnabled(value != null);
		presentation.setVisible(value != null);
	}

	@Nullable
	private static Value getValue(final DebuggerTreeNodeImpl node)
	{
		if (node == null)
		{
			return null;
		}
		NodeDescriptorImpl descriptor = node.getDescriptor();
		return descriptor instanceof ValueDescriptor valueDescriptor ? valueDescriptor.getValue() : null;
	}
}