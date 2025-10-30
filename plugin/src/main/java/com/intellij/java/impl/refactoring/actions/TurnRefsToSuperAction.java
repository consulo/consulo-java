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

import com.intellij.java.impl.refactoring.turnRefsToSuper.TurnRefsToSuperHandler;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.dataContext.DataContext;
import consulo.java.localize.JavaLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;

import jakarta.annotation.Nonnull;

@ActionImpl(id = "TurnRefsToSuper")
public class TurnRefsToSuperAction extends BaseRefactoringAction {
    public TurnRefsToSuperAction() {
        super(JavaLocalize.actionTurnRefsToSuperText(), JavaLocalize.actionTurnRefsToSuperDescription());
    }

    @Override
    public boolean isAvailableInEditorOnly() {
        return false;
    }

    @Override
    @RequiredReadAction
    public boolean isEnabledOnElements(@Nonnull PsiElement[] elements) {
        return elements.length == 1 && elements[0] instanceof PsiClass psiClass && psiClass.getLanguage() == JavaLanguage.INSTANCE;
    }

    @Override
    public RefactoringActionHandler getHandler(@Nonnull DataContext dataContext) {
        return new TurnRefsToSuperHandler();
    }
}