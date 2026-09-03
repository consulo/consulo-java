// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.actions;

import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.engine.AsyncStacksUtils;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerActions;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareToggleAction;

@ActionImpl(id = "Debugger.AsyncStacks", parents = @ActionParentRef(@ActionRef(id = XDebuggerActions.FRAMES_TREE_POPUP_GROUP)))
public class AsyncStacksToggleAction extends DumbAwareToggleAction {
    public AsyncStacksToggleAction() {
        super(JavaDebuggerLocalize.actionDebuggerAsyncstacksText());
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
        XDebugSession session = e.getData(XDebugSession.DATA_KEY);
        return session == null || AsyncStacksUtils.isAsyncStacksEnabled(session);
    }

    @Override
    @RequiredUIAccess
    public void setSelected(AnActionEvent e, boolean state) {
        XDebugSession session = e.getData(XDebugSession.DATA_KEY);
        if (session != null) {
            AsyncStacksUtils.setAsyncStacksEnabled(session, state);
            if (session.isSuspended()) {
                session.rebuildViews();
            }
        }
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabledAndVisible(DebuggerUtilsEx.isInJavaSession(e));
    }
}
