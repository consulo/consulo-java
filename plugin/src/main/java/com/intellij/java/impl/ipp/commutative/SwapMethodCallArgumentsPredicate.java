/*
 * Copyright 2006 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.commutative;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import jakarta.annotation.Nonnull;

class SwapMethodCallArgumentsPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(@Nonnull PsiElement element) {
    if (!(element instanceof PsiExpressionList)) {
      return false;
    }
    PsiExpressionList argumentList = (PsiExpressionList)element;
    PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length != 2) {
      return false;
    }
    PsiElement parent = argumentList.getParent();
    if (!(parent instanceof PsiCallExpression)) {
      return false;
    }
    PsiCallExpression methodCallExpression = (PsiCallExpression)parent;
    PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    PsiParameterList parameterList = method.getParameterList();
    PsiParameter[] parameters = parameterList.getParameters();
    if (parameters.length != 2) {
      return false;
    }
    PsiParameter firstParameter = parameters[0];
    PsiParameter secondParameter = parameters[1];
    PsiType firstType = firstParameter.getType();
    PsiType secondType = secondParameter.getType();
    return firstType.equals(secondType);
  }
}
