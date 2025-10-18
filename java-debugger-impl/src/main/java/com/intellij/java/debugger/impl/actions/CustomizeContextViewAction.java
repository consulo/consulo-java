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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.impl.engine.JavaDebugProcess;
import com.intellij.java.debugger.impl.settings.JavaDebuggerSettings;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.localize.XDebuggerLocalize;
import consulo.ide.impl.idea.openapi.options.TabbedConfigurable;
import consulo.ide.impl.idea.openapi.options.ex.SingleConfigurableEditor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.util.List;

public class CustomizeContextViewAction extends DumbAwareAction {
    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        Disposable disposable = Disposable.newDisposable();
        SingleConfigurableEditor editor = new SingleConfigurableEditor(project, new TabbedConfigurable(disposable) {
            @Override
            protected List<Configurable> createConfigurables() {
                return JavaDebuggerSettings.createDataViewsConfigurable();
            }

            @Override
            @RequiredUIAccess
            public void apply() throws ConfigurationException {
                super.apply();
                NodeRendererSettings.getInstance().fireRenderersChanged();
            }

            @Override
            public LocalizeValue getDisplayName() {
                return JavaDebuggerLocalize.titleCustomizeDataViews();
            }

            @Override
            public String getHelpTopic() {
                return "reference.debug.customize.data.view";
            }

            @Override
            @RequiredUIAccess
            protected void createConfigurableTabs() {
                for (Configurable configurable : getConfigurables()) {
                    JComponent component = configurable.createComponent(disposable);
                    assert component != null;
                    component.setBorder(new EmptyBorder(8, 8, 8, 8));
                    myTabbedPane.addTab(configurable.getDisplayName().get(), component);
                }
            }
        });
        Disposer.register(editor.getDisposable(), disposable);
        editor.show();
    }

    @Override
    @RequiredUIAccess
    public void update(AnActionEvent e) {
        e.getPresentation().setTextValue(XDebuggerLocalize.actionCustomizeContextViewText());

        Project project = e.getData(Project.KEY);
        XDebuggerManager debuggerManager = project == null ? null : XDebuggerManager.getInstance(project);
        XDebugSession currentSession = debuggerManager == null ? null : debuggerManager.getCurrentSession();
        if (currentSession != null) {
            e.getPresentation().setEnabledAndVisible(currentSession.getDebugProcess() instanceof JavaDebugProcess);
        }
        else {
            e.getPresentation().setEnabledAndVisible(false);
        }
    }
}
