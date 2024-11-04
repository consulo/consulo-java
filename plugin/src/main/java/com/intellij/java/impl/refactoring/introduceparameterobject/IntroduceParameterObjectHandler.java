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
package com.intellij.java.impl.refactoring.introduceparameterobject;

import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiParameterList;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.codeEditor.ScrollingModel;
import consulo.dataContext.DataContext;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;

public class IntroduceParameterObjectHandler implements RefactoringActionHandler {
    private static final LocalizeValue REFACTORING_NAME = JavaRefactoringLocalize.introduceParameterObject();

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        final ScrollingModel scrollingModel = editor.getScrollingModel();
        scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE);
        final PsiElement element = dataContext.getData(PsiElement.KEY);
        PsiMethod selectedMethod = null;
        if (element instanceof PsiMethod) {
            selectedMethod = (PsiMethod)element;
        }
        else if (element instanceof PsiParameter parameter && parameter.getDeclarationScope() instanceof PsiMethod methodScope) {
            selectedMethod = methodScope;
        }
        else {
            final CaretModel caretModel = editor.getCaretModel();
            final int position = caretModel.getOffset();
            final PsiElement elementAt = file.findElementAt(position);
            final PsiMethodCallExpression methodCallExpression =
                PsiTreeUtil.getParentOfType(elementAt, PsiMethodCallExpression.class);
            if (methodCallExpression != null) {
                selectedMethod = methodCallExpression.resolveMethod();
            }
            else {
                final PsiParameterList parameterList = PsiTreeUtil.getParentOfType(elementAt, PsiParameterList.class);
                if (parameterList != null && parameterList.getParent() instanceof PsiMethod) {
                    selectedMethod = (PsiMethod)parameterList.getParent();
                }
            }
        }
        if (selectedMethod == null) {
            LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
                JavaRefactoringLocalize.theCaretShouldBePositionedAtTheNameOfTheMethodToBeRefactored()
            );
            CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME.get(), HelpID.IntroduceParameterObject);
            return;
        }
        invoke(project, selectedMethod, editor);
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        if (elements.length != 1) {
            return;
        }
        final PsiMethod method = PsiTreeUtil.getParentOfType(elements[0], PsiMethod.class, false);
        if (method == null) {
            return;
        }
        Editor editor = dataContext.getData(Editor.KEY);
        invoke(project, method, editor);
    }

    @RequiredUIAccess
    private static void invoke(final Project project, final PsiMethod selectedMethod, Editor editor) {
        PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(selectedMethod, RefactoringLocalize.toRefactor().get());
        if (newMethod == null) {
            return;
        }
        if (!CommonRefactoringUtil.checkReadOnlyStatus(project, newMethod)) {
            return;
        }

        final PsiParameter[] parameters = newMethod.getParameterList().getParameters();
        if (parameters.length == 0) {
            LocalizeValue message =
                RefactoringLocalize.cannotPerformRefactoringWithReason(JavaRefactoringLocalize.methodSelectedHasNoParameters());
            CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME.get(), HelpID.IntroduceParameterObject);
            return;
        }
        if (newMethod instanceof PsiCompiledElement) {
            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                RefactoringLocalize.cannotPerformRefactoringWithReason(
                    JavaRefactoringLocalize.theSelectedMethodCannotBeWrappedBecauseItIsDefinedInANonProjectClass()
                ).get(),
                REFACTORING_NAME.get(),
                HelpID.IntroduceParameterObject
            );
            return;
        }
        new IntroduceParameterObjectDialog(newMethod).show();
    }
}
