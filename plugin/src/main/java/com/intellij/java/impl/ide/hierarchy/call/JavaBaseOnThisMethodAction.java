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
package com.intellij.java.impl.ide.hierarchy.call;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.ide.impl.idea.ide.hierarchy.CallHierarchyBrowserBase;

@ActionImpl(
    id = "CallHierarchy.BaseOnThisType",
    parents = @ActionParentRef(value = @ActionRef(id = "CallHierarchyPopupMenu"), anchor = ActionRefAnchor.FIRST)
)
public final class JavaBaseOnThisMethodAction extends CallHierarchyBrowserBase.BaseOnThisMethodAction {
}
