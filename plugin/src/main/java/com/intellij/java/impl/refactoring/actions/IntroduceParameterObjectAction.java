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
package com.intellij.java.impl.refactoring.actions;

import com.intellij.java.impl.refactoring.introduceparameterobject.IntroduceParameterObjectHandler;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.dataContext.DataContext;
import consulo.java.localize.JavaLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;
import jakarta.annotation.Nonnull;

@ActionImpl(
    id = "IntroduceParameterObject",
    parents = @ActionParentRef(
        value = @ActionRef(id = "IntroduceActionsGroup"),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = "IntroduceParameter")
    )
)
public class IntroduceParameterObjectAction extends BaseRefactoringAction {
    public IntroduceParameterObjectAction() {
        super(JavaLocalize.actionIntroduceParameterObjectText(), JavaLocalize.actionIntroduceParameterObjectDescription());
    }

    @Override
    protected boolean isAvailableInEditorOnly() {
        return false;
    }

    @Override
    protected boolean isEnabledOnElements(@Nonnull PsiElement[] elements) {
        return elements.length == 1 && PsiTreeUtil.getParentOfType(elements[0], PsiMethod.class, false) != null;
    }

    @Override
    protected RefactoringActionHandler getHandler(@Nonnull DataContext context) {
        return new IntroduceParameterObjectHandler();
    }
}
