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
package com.intellij.refactoring.actions;

import javax.annotation.Nonnull;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodHandler;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodAction extends BaseRefactoringAction {
  protected boolean isAvailableInEditorOnly() {
    return false;
  }

  protected boolean isEnabledOnElements(@Nonnull PsiElement[] elements) {
    return elements.length == 1 && elements[0] instanceof PsiMethod;
  }

  protected boolean isAvailableOnElementInEditorAndFile(@Nonnull PsiElement element, @Nonnull final Editor editor, @Nonnull PsiFile file, @Nonnull DataContext context) {
    if (element instanceof PsiIdentifier) element = element.getParent();
    return element instanceof PsiMethod && ((PsiMethod) element).hasModifierProperty(PsiModifier.STATIC);
  }

  protected RefactoringActionHandler getHandler(@Nonnull DataContext dataContext) {
    return new ConvertToInstanceMethodHandler();
  }
}
