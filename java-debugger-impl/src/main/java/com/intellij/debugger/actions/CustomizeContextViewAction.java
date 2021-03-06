/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.debugger.actions;

import java.util.List;

import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;

import javax.annotation.Nonnull;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.settings.JavaDebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.idea.ActionsBundle;
import consulo.disposer.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.TabbedConfigurable;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import consulo.disposer.Disposer;

public class CustomizeContextViewAction extends XDebuggerTreeActionBase
{
	@Override
	public void actionPerformed(AnActionEvent e)
	{
		perform(null, "", e);
	}

	@Override
	protected void perform(XValueNodeImpl node, @Nonnull String nodeName, AnActionEvent e)
	{
		final Project project = e.getData(CommonDataKeys.PROJECT);
		Disposable disposable = Disposable.newDisposable();
		SingleConfigurableEditor editor = new SingleConfigurableEditor(project, new TabbedConfigurable(disposable)
		{
			@Override
			protected List<Configurable> createConfigurables()
			{
				return JavaDebuggerSettings.createDataViewsConfigurable();
			}

			@Override
			public void apply() throws ConfigurationException
			{
				super.apply();
				NodeRendererSettings.getInstance().fireRenderersChanged();
			}

			@Override
			public String getDisplayName()
			{
				return DebuggerBundle.message("title.customize.data.views");
			}

			@Override
			public String getHelpTopic()
			{
				return "reference.debug.customize.data.view";
			}

			@Override
			protected void createConfigurableTabs()
			{
				for(Configurable configurable : getConfigurables())
				{
					JComponent component = configurable.createComponent();
					assert component != null;
					component.setBorder(new EmptyBorder(8, 8, 8, 8));
					myTabbedPane.addTab(configurable.getDisplayName(), component);
				}
			}
		});
		Disposer.register(editor.getDisposable(), disposable);
		editor.show();
	}

	@Override
	public void update(AnActionEvent e)
	{
		e.getPresentation().setText(ActionsBundle.actionText(DebuggerActions.CUSTOMIZE_VIEWS));

		Project project = e.getProject();
		final XDebuggerManager debuggerManager = project == null ? null : XDebuggerManager.getInstance(project);
		final XDebugSession currentSession = debuggerManager == null ? null : debuggerManager.getCurrentSession();
		if(currentSession != null)
		{
			e.getPresentation().setEnabledAndVisible(currentSession.getDebugProcess() instanceof JavaDebugProcess);
		}
		else
		{
			e.getPresentation().setEnabledAndVisible(false);
		}
	}
}
