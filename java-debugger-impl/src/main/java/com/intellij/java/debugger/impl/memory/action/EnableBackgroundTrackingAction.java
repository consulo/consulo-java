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

import com.intellij.java.debugger.impl.memory.component.InstancesTracker;
import consulo.annotation.component.ActionImpl;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;

@ActionImpl(id = "MemoryView.EnableTrackingWithClosedWindow")
public class EnableBackgroundTrackingAction extends ToggleAction {
    public EnableBackgroundTrackingAction() {
        super("Enable Tracking With Hidden Memory View");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        return project != null && !project.isDisposed() && InstancesTracker.getInstance(project).isBackgroundTrackingEnabled();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
        Project project = e.getData(Project.KEY);
        if (project != null && !project.isDisposed()) {
            InstancesTracker.getInstance(project).setBackgroundTackingEnabled(state);
        }
    }
}
