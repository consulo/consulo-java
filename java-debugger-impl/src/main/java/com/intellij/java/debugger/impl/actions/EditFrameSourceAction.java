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

import consulo.annotation.component.ActionImpl;
import consulo.execution.debug.localize.XDebuggerLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.IdeActions;

/**
 * @author lex
 */
@ActionImpl(id = DebuggerActions.EDIT_FRAME_SOURCE)
public class EditFrameSourceAction extends GotoFrameSourceAction {
    public EditFrameSourceAction() {
        super(XDebuggerLocalize.actionEditFrameSourceText());
    }

    @Override
    @RequiredUIAccess
    public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setTextValue(
            ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getTemplatePresentation().getTextValue()
        );
    }
}
