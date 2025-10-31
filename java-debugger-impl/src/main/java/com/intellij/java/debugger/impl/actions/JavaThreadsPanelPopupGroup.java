/*
 * Copyright 2013-2025 consulo.io
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
import consulo.annotation.component.ActionRef;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/**
 * @author UNV
 * @since 2025-10-30
 */
@ActionImpl(
    id = DebuggerActions.THREADS_PANEL_POPUP,
    children = {
        //@ActionRef(type = ResumeThreadAction.class),
        //@ActionRef(type = FreezeThreadAction.class),
        @ActionRef(type = InterruptThreadAction.class),
        //@ActionRef(id = "Debugger.ShowFrame"),
        @ActionRef(id = DebuggerActions.POP_FRAME),
        //@ActionRef(id = DebuggerActions.EDIT_FRAME_SOURCE),
        //@ActionRef(id = "Debugger.EditTypeSource"),
        //@ActionRef(id = "EditSource"),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = ExportThreadsAction.class),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = CustomizeThreadsViewAction.class)
    }
)
public class JavaThreadsPanelPopupGroup extends DefaultActionGroup implements DumbAware {
    public JavaThreadsPanelPopupGroup() {
        super(LocalizeValue.empty(), false);
    }
}
