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
package com.intellij.java.impl.refactoring.wrapreturnvalue;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import com.intellij.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;

public class WrapReturnValueAction extends BaseRefactoringAction{

  protected RefactoringActionHandler getHandler(@Nonnull DataContext context){
        return new WrapReturnValueHandler();
    }

  public boolean isAvailableInEditorOnly(){
      return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@Nonnull PsiElement element, @Nonnull Editor editor, @Nonnull PsiFile file, @Nonnull DataContext context) {
    final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (psiMethod != null && !(psiMethod instanceof PsiCompiledElement)) {
      final PsiType returnType = psiMethod.getReturnType();
      return returnType != null && !PsiType.VOID.equals(returnType);
    }
    return false;
  }

  public boolean isEnabledOnElements(@Nonnull PsiElement[] elements) {
    if (elements.length != 1) {
        return false;
    }
    final PsiElement element = elements[0];
    final PsiMethod containingMethod =
            PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    return containingMethod != null;
  }
}
