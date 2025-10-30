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
package com.intellij.java.impl.ide.hierarchy.method;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/**
 * @author UNV
 * @since 2025-10-30
 */
@ActionImpl(
    id = "JavaMethodHierarchyPopupMenu",
    children = {
        @ActionRef(type = ImplementMethodAction.class),
        @ActionRef(type = OverrideMethodAction.class),
        @ActionRef(type = AnSeparator.class)
    },
    parents = @ActionParentRef(value = @ActionRef(id = "MethodHierarchyPopupMenu"), anchor = ActionRefAnchor.FIRST)
)
public class JavaMethodHierarchyPopupMenuGroup extends DefaultActionGroup implements DumbAware {
    public JavaMethodHierarchyPopupMenuGroup() {
        super(LocalizeValue.empty(), false);
    }
}
