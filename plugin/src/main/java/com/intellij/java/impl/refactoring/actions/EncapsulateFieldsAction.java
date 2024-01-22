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

import com.intellij.java.impl.refactoring.encapsulateFields.EncapsulateFieldsHandler;
import com.intellij.java.language.JavaLanguage;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;
import jakarta.annotation.Nonnull;

public class EncapsulateFieldsAction extends BaseRefactoringAction {
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@jakarta.annotation.Nonnull PsiElement element, @jakarta.annotation.Nonnull Editor editor, @jakarta.annotation.Nonnull PsiFile file, @Nonnull DataContext context) {
    final PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, false);
    if (containingClass != null) {
      final PsiField[] fields = containingClass.getFields();
      for (PsiField field : fields) {
        if (isAcceptedField(field)) return true;
      }
    }
    return false;
  }

  public boolean isEnabledOnElements(@jakarta.annotation.Nonnull PsiElement[] elements) {
    if (elements.length == 1) {
      return elements[0] instanceof PsiClass && elements[0].getLanguage().isKindOf(JavaLanguage.INSTANCE) || isAcceptedField(elements[0]);
    } else if (elements.length > 1) {
      for (int idx = 0; idx < elements.length; idx++) {
        if (!isAcceptedField(elements[idx])) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public RefactoringActionHandler getHandler(@jakarta.annotation.Nonnull DataContext dataContext) {
    return new EncapsulateFieldsHandler();
  }

  private static boolean isAcceptedField(PsiElement element) {
    return element instanceof PsiField &&
        element.getLanguage().isKindOf(JavaLanguage.INSTANCE) &&
        ((PsiField) element).getContainingClass() != null;
  }
}