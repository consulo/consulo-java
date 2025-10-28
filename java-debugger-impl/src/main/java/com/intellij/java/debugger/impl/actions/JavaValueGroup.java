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
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.DefaultActionGroup;

/**
 * @author UNV
 * @since 2025-10-08
 */
@ActionImpl(
    id = "Java.XDebugger.ValueGroup",
    children = {
        @ActionRef(type = ViewAsGroup.class),
        @ActionRef(type = AdjustArrayRangeAction.class),
        @ActionRef(type = ForceOnDemandRenderersAction.class)
    },
    parents = @ActionParentRef(value = @ActionRef(id = "XDebugger.ValueGroup"))
)
public class JavaValueGroup extends DefaultActionGroup implements DumbAware {
    public JavaValueGroup() {
        super(LocalizeValue.empty(), false);
    }
}
