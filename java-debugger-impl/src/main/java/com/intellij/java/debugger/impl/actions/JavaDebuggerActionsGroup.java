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

import com.intellij.java.debugger.impl.ui.tree.actions.ForceOnDemandRenderersAction;
import com.intellij.java.debugger.localize.JavaDebuggerLocalize;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.dumb.DumbAware;
import consulo.execution.debug.XDebuggerActions;
import consulo.ui.ex.action.DefaultActionGroup;

/**
 * @author UNV
 * @since 2025-10-15
 */
@ActionImpl(
    id = "JavaDebuggerActions",
    children = {
        @ActionRef(type = CustomizeContextViewAction.class),
        @ActionRef(type = CustomizeThreadsViewAction.class),
        @ActionRef(type = EditFrameSourceAction.class),
        @ActionRef(type = EditSourceAction.class),
        @ActionRef(type = JumpToObjectAction.class),
        @ActionRef(id = DebuggerActions.POP_FRAME),
        @ActionRef(type = ViewAsGroup.class),
        @ActionRef(type = AdjustArrayRangeAction.class),
        @ActionRef(type = ResumeThreadAction.class),
        @ActionRef(type = FreezeThreadAction.class),
        @ActionRef(type = InterruptThreadAction.class),
        @ActionRef(type = CreateRendererAction.class),
        @ActionRef(type = AutoRendererAction.class),
        @ActionRef(type = ForceOnDemandRenderersAction.class)
    },
    parents = @ActionParentRef(value = @ActionRef(id = XDebuggerActions.KEYMAP_GROUP), anchor = ActionRefAnchor.FIRST)
)
public class JavaDebuggerActionsGroup extends DefaultActionGroup implements DumbAware {
    public JavaDebuggerActionsGroup() {
        super(JavaDebuggerLocalize.groupJavaDebuggerActionsText(), false);
    }
}
