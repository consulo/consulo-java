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
 * User: anna
 * Date: 07-May-2008
 */
package com.intellij.java.impl.refactoring.replaceConstructorWithBuilder;

import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.refactoring.HelpID;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class ReplaceConstructorWithBuilderHandler implements RefactoringActionHandler {
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    PsiClass psiClass = getParentNamedClass(element);
    if (psiClass == null) {
      showErrorMessage("The caret should be positioned inside a class which constructors are to be replaced with builder.", project, editor);
      return;
    }

    PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) {
      showErrorMessage("Current class doesn't have constructors to replace with builder.", project, editor);
      return;
    }

    new ReplaceConstructorWithBuilderDialog(project, constructors).show();
  }

  @Nullable
  public static PsiClass getParentNamedClass(PsiElement element) {
    if (element != null) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        PsiElement resolve = ((PsiJavaCodeReferenceElement)parent).resolve();
        if (resolve instanceof PsiClass) return (PsiClass)resolve;
      }
    }
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass instanceof PsiAnonymousClass) {
      return getParentNamedClass(psiClass);
    }
    return psiClass;
  }

  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    throw new UnsupportedOperationException();
  }

  private static void showErrorMessage(String message, Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, ReplaceConstructorWithBuilderProcessor.REFACTORING_NAME,  HelpID.REPLACE_CONSTRUCTOR_WITH_BUILDER);
  }
}
