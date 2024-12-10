/*
 * Copyright 2013-2017 consulo.io
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

package com.intellij.java.debugger.impl.ui.tree.actions;

import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.settings.NodeRendererSettings;
import consulo.application.dumb.DumbAware;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.ui.XDebugSessionData;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;
import consulo.util.dataholder.Key;

/**
 * from kotlin
 */
public class ForceOnDemandRenderersAction extends ToggleAction implements DumbAware {
    private static final Key<Boolean> RENDERERS_ONDEMAND_FORCED = Key.create("RENDERERS_ONDEMAND_FORCED");

    private static XDebugSessionData getSessionData(AnActionEvent e) {
        XDebugSessionData data = e.getData(XDebugSessionData.DATA_KEY);
        if (data == null) {
            Project project = e.getData(Project.KEY);
            if (project != null) {
                XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
                if (session != null) {
                    data = ((XDebugSession) session).getSessionData();
                }
            }
        }
        return data;
    }

    public static boolean isForcedOnDemand(XDebugSession session) {
        return RENDERERS_ONDEMAND_FORCED.get(session.getSessionData(), false);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
        return RENDERERS_ONDEMAND_FORCED.get(getSessionData(e), false);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
        RENDERERS_ONDEMAND_FORCED.set(getSessionData(e), state);
        NodeRendererSettings.getInstance().fireRenderersChanged();
    }

    @RequiredUIAccess
    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(DebuggerUtilsEx.isInJavaSession(e));
    }
}
