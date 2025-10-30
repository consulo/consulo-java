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
package com.intellij.java.impl.ide.hierarchy.type;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.ide.impl.idea.ide.hierarchy.HierarchyBrowserBaseEx;
import consulo.ide.impl.idea.ide.hierarchy.TypeHierarchyBrowserBase;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

@ActionImpl(
    id = "TypeHierarchy.BaseOnThisType",
    parents = @ActionParentRef(value = @ActionRef(id = "TypeHierarchyPopupMenu"), anchor = ActionRefAnchor.FIRST)
)
public class JavaBaseOnThisTypeAction extends TypeHierarchyBrowserBase.BaseOnThisTypeAction {
    @Override
    @RequiredReadAction
    protected boolean isEnabled(@Nonnull HierarchyBrowserBaseEx browser, @Nonnull PsiElement psiElement) {
        return super.isEnabled(browser, psiElement)
            && !CommonClassNames.JAVA_LANG_OBJECT.equals(((PsiClass) psiElement).getQualifiedName());
    }
}
