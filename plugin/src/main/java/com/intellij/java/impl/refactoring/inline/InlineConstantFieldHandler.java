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

import consulo.language.editor.TargetElementUtil;
import com.intellij.java.language.JavaLanguage;
import consulo.codeEditor.Editor;
import consulo.application.progress.ProgressManager;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.PsiReference;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.psi.search.ProjectScope;
import consulo.language.psi.search.ReferencesSearch;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;

/**
 * @author ven
 */
public class InlineConstantFieldHandler extends JavaInlineActionHandler {
  private static final String REFACTORING_NAME = RefactoringBundle.message("inline.field.title");

  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiField && JavaLanguage.INSTANCE.equals(element.getLanguage());
  }

  public void inlineElement(Project project, Editor editor, PsiElement element) {
    final PsiElement navigationElement = element.getNavigationElement();
    final PsiField field = (PsiField)(navigationElement instanceof PsiField ? navigationElement : element);

    if (!field.hasInitializer()) {
      String message = RefactoringBundle.message("no.initializer.present.for.the.field");
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
      return;
    }

    if (field instanceof PsiEnumConstant) {
      String message = REFACTORING_NAME + " is not supported for enum constants";
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
      return;
    }

    if (ReferencesSearch.search(field, ProjectScope.getProjectScope(project), false).findFirst() == null) {
      String message = RefactoringBundle.message("field.0.is.never.used", field.getName());
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
      return;
    }

    if (!field.hasModifierProperty(PsiModifier.FINAL)) {
      final Ref<Boolean> hasWriteUsages = new Ref<Boolean>(false);
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          for (PsiReference reference : ReferencesSearch.search(field)) {
            final PsiElement referenceElement = reference.getElement();
            if (!(referenceElement instanceof PsiExpression && PsiUtil.isAccessedForReading((PsiExpression)referenceElement))) {
              hasWriteUsages.set(true);
              break;
            }
          }
        }
      }, "Check if inline is possible...", true, project)) {
        return;
      }
      if (hasWriteUsages.get()) {
        String message = RefactoringBundle.message("0.refactoring.is.supported.only.for.final.fields", REFACTORING_NAME);
        CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INLINE_FIELD);
        return;
      }
    }

    PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
    if (reference != null) {
      final PsiElement resolve = reference.resolve();
      if (resolve != null && !field.equals(resolve.getNavigationElement())) {
        reference = null;
      }
    }

    if ((!(element instanceof PsiCompiledElement) || reference == null) && !CommonRefactoringUtil.checkReadOnlyStatus(project, field)) return;
    PsiReferenceExpression refExpression = reference instanceof PsiReferenceExpression ? (PsiReferenceExpression)reference : null;
    InlineFieldDialog dialog = new InlineFieldDialog(project, field, refExpression);
    dialog.show();
  }
}
