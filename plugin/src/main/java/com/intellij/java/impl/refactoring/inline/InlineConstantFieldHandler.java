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
package com.intellij.java.impl.refactoring.inline;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressManager;
import consulo.codeEditor.Editor;
import consulo.java.localize.JavaRefactoringLocalize;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.ref.SimpleReference;

/**
 * @author ven
 */
@ExtensionImpl
public class InlineConstantFieldHandler extends JavaInlineActionHandler {
    private static final LocalizeValue REFACTORING_NAME = RefactoringLocalize.inlineFieldTitle();

    @Override
    @RequiredReadAction
    public boolean canInlineElement(PsiElement element) {
        return element instanceof PsiField && JavaLanguage.INSTANCE.equals(element.getLanguage());
    }

    @Override
    @RequiredUIAccess
    public void inlineElement(Project project, Editor editor, PsiElement element) {
        PsiField field = element.getNavigationElement() instanceof PsiField psiField ? psiField : (PsiField) element;

        if (!field.hasInitializer()) {
            LocalizeValue message = RefactoringLocalize.noInitializerPresentForTheField();
            CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME.get(), HelpID.INLINE_FIELD);
            return;
        }

        if (field instanceof PsiEnumConstant) {
            LocalizeValue message = JavaRefactoringLocalize.inlineConstantFieldNotSupportedForEnumConstants(REFACTORING_NAME);
            CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME.get(), HelpID.INLINE_FIELD);
            return;
        }

        if (ReferencesSearch.search(field, ProjectScopes.getProjectScope(project), false).findFirst() == null) {
            LocalizeValue message = RefactoringLocalize.field0IsNeverUsed(field.getName());
            CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME.get(), HelpID.INLINE_FIELD);
            return;
        }

        if (!field.isFinal()) {
            SimpleReference<Boolean> hasWriteUsages = new SimpleReference<Boolean>(false);
            if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> {
                    for (PsiReference reference : ReferencesSearch.search(field)) {
                        if (!(reference.getElement() instanceof PsiExpression expr && PsiUtil.isAccessedForReading(expr))) {
                            hasWriteUsages.set(true);
                            break;
                        }
                    }
                },
                JavaRefactoringLocalize.inlineConflictsProgress(),
                true,
                project
            )) {
                return;
            }
            if (hasWriteUsages.get()) {
                LocalizeValue message = RefactoringLocalize.zeroRefactoringIsSupportedOnlyForFinalFields(REFACTORING_NAME);
                CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME.get(), HelpID.INLINE_FIELD);
                return;
            }
        }

        PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
        if (reference != null) {
            PsiElement resolve = reference.resolve();
            if (resolve != null && !field.equals(resolve.getNavigationElement())) {
                reference = null;
            }
        }

        if ((!(element instanceof PsiCompiledElement) || reference == null) && !CommonRefactoringUtil.checkReadOnlyStatus(project, field)) {
            return;
        }
        PsiReferenceExpression refExpression = reference instanceof PsiReferenceExpression refExpr ? refExpr : null;
        InlineFieldDialog dialog = new InlineFieldDialog(project, field, refExpression);
        dialog.show();
    }
}
