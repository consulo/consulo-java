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

import com.intellij.java.impl.refactoring.invertBoolean.InvertBooleanHandler;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiVariable;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;

import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
public class InvertBooleanAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isEnabledOnElements(@Nonnull PsiElement[] elements) {
    return elements.length == 1 && (elements[0] instanceof PsiMethod || elements[0] instanceof PsiVariable);
  }

  protected boolean isAvailableOnElementInEditorAndFile(@Nonnull final PsiElement element, @Nonnull final Editor editor, @Nonnull PsiFile file, @Nonnull DataContext context) {
    if (element instanceof PsiVariable) {
      return PsiType.BOOLEAN.equals(((PsiVariable) element).getType());
    } else if (element instanceof PsiMethod) {
      return PsiType.BOOLEAN.equals(((PsiMethod) element).getReturnType());
    }
    return false;
  }

  protected RefactoringActionHandler getHandler(@Nonnull DataContext dataContext) {
    return new InvertBooleanHandler();
  }
}
