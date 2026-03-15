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

import com.intellij.java.impl.refactoring.tempWithQuery.TempWithQueryHandler;
import com.intellij.java.language.psi.PsiLocalVariable;
import consulo.annotation.component.ActionImpl;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.java.localize.JavaLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;

@ActionImpl(id = "ReplaceTempWithQuery")
public class TempWithQueryAction extends BaseRefactoringAction {
    public TempWithQueryAction() {
        super(JavaLocalize.actionReplaceTempWithQueryText(), JavaLocalize.actionReplaceTempWithQueryDescription());
    }

    @Override
    public boolean isAvailableInEditorOnly() {
        return true;
    }

    @Override
    public boolean isEnabledOnElements(PsiElement[] elements) {
        return false;
    }

    @Override
    public RefactoringActionHandler getHandler(DataContext dataContext) {
        return new TempWithQueryHandler();
    }

    @Override
    protected boolean isAvailableOnElementInEditorAndFile(
        PsiElement element,
        Editor editor,
        PsiFile file,
        DataContext context
    ) {
        return element instanceof PsiLocalVariable localVar && localVar.getInitializer() != null;
    }
}