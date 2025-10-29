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

/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: Apr 15, 2002
 * Time: 1:32:20 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.java.impl.refactoring.actions;

import com.intellij.java.impl.refactoring.makeStatic.MakeStaticHandler;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiTypeParameterListOwner;
import consulo.annotation.component.ActionImpl;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.java.localize.JavaLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;

import jakarta.annotation.Nonnull;

@ActionImpl(id = "MakeStatic")
public class MakeStaticAction extends BaseRefactoringAction {
    public MakeStaticAction() {
        super(JavaLocalize.actionMakestaticText(), JavaLocalize.actionMakestaticDescription());
    }

    @Override
    protected boolean isAvailableInEditorOnly() {
        return false;
    }

    @Override
    protected boolean isEnabledOnElements(@Nonnull PsiElement[] elements) {
        return elements.length == 1 && elements[0] instanceof PsiMethod method && !method.isConstructor();
    }

    @Override
    protected boolean isAvailableOnElementInEditorAndFile(
        @Nonnull PsiElement element,
        @Nonnull Editor editor,
        @Nonnull PsiFile file,
        @Nonnull DataContext context
    ) {
        if (element instanceof PsiIdentifier) {
            element = element.getParent();
        }
        return element instanceof PsiTypeParameterListOwner paramListOwner && MakeStaticHandler.validateTarget(paramListOwner) == null;
    }

    @Override
    protected RefactoringActionHandler getHandler(@Nonnull DataContext dataContext) {
        return new MakeStaticHandler();
    }
}
