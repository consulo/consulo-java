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
package com.intellij.java.impl.refactoring.turnRefsToSuper;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
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

import java.util.List;

/**
 * @author Jeka
 * @since 2001-10-25
 */
public class TurnRefsToSuperHandler implements RefactoringActionHandler {
    public static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.useInterfaceWherePossibleTitle();

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        int offset = editor.getCaretModel().getOffset();
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        PsiElement element = file.findElementAt(offset);
        while (true) {
            if (element == null || element instanceof PsiFile) {
                LocalizeValue message =
                    RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.errorWrongCaretPositionClass());
                CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.TURN_REFS_TO_SUPER);
                return;
            }
            if (element instanceof PsiClass psiClass && !(psiClass instanceof PsiAnonymousClass)) {
                invoke(project, new PsiElement[]{psiClass}, dataContext);
                return;
            }
            element = element.getParent();
        }
    }

    @Override
    @RequiredUIAccess
    public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
        if (elements.length != 1) {
            return;
        }

        PsiClass subClass = (PsiClass) elements[0];

        List basesList = RefactoringHierarchyUtil.createBasesList(subClass, true, true);

        if (basesList.isEmpty()) {
            LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
                RefactoringLocalize.interfaceDoesNotHaveBaseInterfaces(subClass.getQualifiedName())
            );
            Editor editor = dataContext.getData(Editor.KEY);
            CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.TURN_REFS_TO_SUPER);
            return;
        }

        new TurnRefsToSuperDialog(project, subClass, basesList).show();
    }
}
