/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.java.impl.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryHandler;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ActionImpl;
import consulo.dataContext.DataContext;
import consulo.java.localize.JavaLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;

import jakarta.annotation.Nonnull;

/**
 * @author dsl
 */
@ActionImpl(id = "ReplaceConstructorWithFactory")
public class ReplaceConstructorWithFactoryAction extends BaseRefactoringAction {
    public ReplaceConstructorWithFactoryAction() {
        super(JavaLocalize.actionReplaceConstructorWithFactoryText(), JavaLocalize.actionReplaceConstructorWithFactoryDescription());
    }

    @Override
    protected boolean isAvailableInEditorOnly() {
        return false;
    }

    @Override
    @RequiredReadAction
    protected boolean isEnabledOnElements(@Nonnull PsiElement[] elements) {
        return elements.length == 1
            && (elements[0] instanceof PsiMethod method && method.isConstructor() || elements[0] instanceof PsiClass)
            && elements[0].getLanguage().isKindOf(JavaLanguage.INSTANCE);
    }

    @Override
    protected RefactoringActionHandler getHandler(@Nonnull DataContext dataContext) {
        return new ReplaceConstructorWithFactoryHandler();
    }
}
