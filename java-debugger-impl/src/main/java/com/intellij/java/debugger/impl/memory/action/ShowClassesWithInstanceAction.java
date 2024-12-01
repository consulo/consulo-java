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

import com.intellij.java.debugger.impl.memory.component.MemoryViewManager;
import consulo.annotation.component.ActionImpl;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;

@ActionImpl(id = "MemoryView.ShowOnlyWithInstances")
public class ShowClassesWithInstanceAction extends ToggleAction {
    public ShowClassesWithInstanceAction() {
        super("Show With Instances Only");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
        return MemoryViewManager.getInstance().isNeedShowInstancesOnly();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
        Project project = e.getData(Project.KEY);
        if (project != null) {
            MemoryViewManager.getInstance().setShowWithInstancesOnly(state);
        }
    }
}
