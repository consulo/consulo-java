/*
 * Copyright 2006-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.varargs;

import javax.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class MakeMethodVarargsPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(@Nonnull PsiElement element) {
    if (!(element instanceof PsiParameterList)) {
      return false;
    }
    if (!PsiUtil.isLanguageLevel5OrHigher(element)) {
      return false;
    }
    final PsiParameterList parameterList = (PsiParameterList)element;
    if (!(element.getParent() instanceof PsiMethod)) {
      return false;
    }
    if (parameterList.getParametersCount() == 0) {
      return false;
    }
    final PsiParameter[] parameters = parameterList.getParameters();
    final PsiParameter lastParameter =
      parameters[parameters.length - 1];
    if (lastParameter.isVarArgs()) {
      return false;
    }
    final PsiType type = lastParameter.getType();
    return type instanceof PsiArrayType;
  }
}