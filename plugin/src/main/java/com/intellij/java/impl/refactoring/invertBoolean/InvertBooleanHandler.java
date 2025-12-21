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
package com.intellij.java.impl.refactoring.invertBoolean;

import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiVariable;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
public class InvertBooleanHandler implements RefactoringActionHandler {
    static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.invertBooleanTitle();

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        PsiElement element = dataContext.getData(PsiElement.KEY);
        if (element instanceof PsiMethod method) {
            invoke(method, project, editor);
        }
        else if (element instanceof PsiVariable variable) {
            invoke(variable, project, editor);
        }
        else {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.errorWrongCaretPositionMethodOrVariableName()),
                REFACTORING_NAME,
                HelpID.INVERT_BOOLEAN
            );
        }
    }

    @RequiredUIAccess
    private static void invoke(PsiVariable var, Project project, Editor editor) {
        PsiType returnType = var.getType();
        if (!PsiType.BOOLEAN.equals(returnType)) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.invertBooleanWrongType()),
                REFACTORING_NAME,
                HelpID.INVERT_BOOLEAN
            );
            return;
        }

        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, var)) {
            return;
        }
        if (var instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod method) {
            PsiMethod superMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringLocalize.toRefactor());
            if (superMethod != null) {
                var = superMethod.getParameterList().getParameters()[method.getParameterList().getParameterIndex(parameter)];
            }
        }

        new InvertBooleanDialog(var).show();
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, @Nonnull DataContext dataContext) {
        if (elements.length == 1 && elements[0] instanceof PsiMethod method) {
            invoke(method, project, null);
        }
    }

    @RequiredUIAccess
    private static void invoke(PsiMethod method, Project project, Editor editor) {
        PsiType returnType = method.getReturnType();
        if (!PsiType.BOOLEAN.equals(returnType)) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.invertBooleanWrongType()),
                REFACTORING_NAME,
                HelpID.INVERT_BOOLEAN
            );
            return;
        }

        PsiMethod superMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringLocalize.toRefactor());
        if (superMethod != null) {
            method = superMethod;
        }

        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) {
            return;
        }

        new InvertBooleanDialog(method).show();
    }
}
