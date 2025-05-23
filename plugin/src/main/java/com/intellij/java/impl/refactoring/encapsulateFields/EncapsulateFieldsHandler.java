/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.refactoring.encapsulateFields;

import java.util.HashSet;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import consulo.dataContext.DataContext;
import consulo.logging.Logger;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import consulo.language.psi.PsiFile;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;

public class EncapsulateFieldsHandler implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(EncapsulateFieldsHandler.class);
  public static final String REFACTORING_NAME = RefactoringBundle.message("encapsulate.fields.title");

  @RequiredReadAction
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.errorWrongCaretPositionClass());
        CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.ENCAPSULATE_FIELDS);
        return;
      }
      if (element instanceof PsiField field) {
        if (field.getContainingClass() == null) {
          LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(RefactoringLocalize.theFieldShouldBeDeclaredInAClass());
          CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.ENCAPSULATE_FIELDS);
          return;
        }
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      if (element instanceof PsiClass) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  /**
   * if elements.length == 1 the expected value is either PsiClass or PsiField
   * if elements.length > 1 the expected values are PsiField objects only
   */
  public void invoke(@Nonnull final Project project, @Nonnull final PsiElement[] elements, DataContext dataContext) {
    PsiClass aClass = null;
    final HashSet<PsiField> preselectedFields = new HashSet<>();
    if (elements.length == 1) {
      if (elements[0] instanceof PsiClass psiClass) {
        aClass = psiClass;
      } else if (elements[0] instanceof PsiField field) {
        aClass = field.getContainingClass();
        preselectedFields.add(field);
      } else {
        return;
      }
    } else {
      for (PsiElement element : elements) {
        if (!(element instanceof PsiField)) {
          return;
        }
        PsiField field = (PsiField)element;
        if (aClass == null) {
          aClass = field.getContainingClass();
          preselectedFields.add(field);
        }
        else {
          if (aClass.equals(field.getContainingClass())) {
            preselectedFields.add(field);
          }
          else {
            LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
                RefactoringLocalize.fieldsToBeRefactoredShouldBelongToTheSameClass()
            );
            Editor editor = dataContext.getData(Editor.KEY);
            CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.ENCAPSULATE_FIELDS);
            return;
          }
        }
      }
    }

    LOG.assertTrue(aClass != null);
    final PsiField[] fields = aClass.getFields();
    if (fields.length == 0) {
      CommonRefactoringUtil.showErrorHint(
        project,
        dataContext.getData(Editor.KEY),
        "Class has no fields to encapsulate",
        REFACTORING_NAME,
        HelpID.ENCAPSULATE_FIELDS
      );
      return;
    }

    if (aClass.isInterface()) {
      LocalizeValue message = RefactoringLocalize.cannotPerformRefactoringWithReason(
        RefactoringLocalize.encapsulateFieldsRefactoringCannotBeAppliedToInterface()
      );
      Editor editor = dataContext.getData(Editor.KEY);
      CommonRefactoringUtil.showErrorHint(project, editor, message.get(), REFACTORING_NAME, HelpID.ENCAPSULATE_FIELDS);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;

    EncapsulateFieldsDialog dialog = createDialog(project, aClass, preselectedFields);
    dialog.show();
  }

  protected EncapsulateFieldsDialog createDialog(Project project, PsiClass aClass, HashSet<PsiField> preselectedFields) {
    return new EncapsulateFieldsDialog(
      project,
      aClass,
      preselectedFields,
      new JavaEncapsulateFieldHelper());
  }
}