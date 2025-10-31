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
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.impl.DebuggerManagerEx;
import com.intellij.java.debugger.impl.DebuggerSession;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.ui.HotSwapUI;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;

/**
 * @author lex
 */
@ActionImpl(
    id = "Hotswap",
    parents = @ActionParentRef(
        value = @ActionRef(id = "DebugMainMenu"),
        anchor = ActionRefAnchor.BEFORE,
        relatedToAction = @ActionRef(id = "StepOver")
    )
)
public class HotSwapAction extends AnAction {
    public HotSwapAction() {
        super(JavaDebuggerLocalize.actionHotswapText(), JavaDebuggerLocalize.actionHotswapDescription());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }

        DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
        DebuggerSession session = debuggerManager.getContext().getDebuggerSession();

        if (session != null && session.isAttached()) {
            HotSwapUI.getInstance(project).reloadChangedClasses(session, DebuggerSettings.getInstance().COMPILE_BEFORE_HOTSWAP);
        }
    }

    @Override
    @RequiredUIAccess
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
        DebuggerSession session = debuggerManager.getContext().getDebuggerSession();

        e.getPresentation().setEnabled(session != null && session.isAttached() && session.getProcess().canRedefineClasses());
    }
}
