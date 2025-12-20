/*
 * Copyright 2007-2009 Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import jakarta.annotation.Nonnull;

class VarargArgumentsPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(@Nonnull PsiElement element) {
    if (!(element instanceof PsiExpressionList)) {
      return false;
    }
    PsiExpressionList argumentList = (PsiExpressionList)element;
    PsiElement grandParent = argumentList.getParent();
    if (!(grandParent instanceof PsiMethodCallExpression)) {
      return false;
    }
    PsiMethodCallExpression methodCallExpression =
      (PsiMethodCallExpression)grandParent;
    PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null || !method.isVarArgs()) {
      return false;
    }
    PsiParameterList parameterList = method.getParameterList();
    int parametersCount = parameterList.getParametersCount();
    PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length < parametersCount) {
      return false;
    }

    // after invoking the (false positive) quick fix for
    // "Unnecessarily qualified static usage" inspection
    // the psi gets into a bad state, this guards against that.
    // http://www.jetbrains.net/jira/browse/IDEADEV-40124
    PsiReferenceExpression methodExpression =
      methodCallExpression.getMethodExpression();
    PsiExpression qualifier =
      methodExpression.getQualifierExpression();
    if (qualifier == null) {
      PsiReferenceParameterList typeParameterList =
        methodExpression.getParameterList();
      if (typeParameterList != null) {
        PsiTypeElement[] typeParameterElements =
          typeParameterList.getTypeParameterElements();
        if (typeParameterElements.length > 0) {
          return false;
        }
      }
    }

    if (arguments.length != parametersCount) {
      return true;
    }
    PsiExpression lastExpression =
      arguments[arguments.length - 1];
    PsiExpression expression = PsiUtil.deparenthesizeExpression(
      lastExpression);
    if (expression instanceof PsiLiteralExpression) {
      String text = expression.getText();
      if ("null".equals(text)) {
        // a single null argument is not wrapped in an array
        // on a vararg method call, but just passed as a null value
        return false;
      }
    }
    PsiType lastArgumentType = lastExpression.getType();
    if (!(lastArgumentType instanceof PsiArrayType)) {
      return true;
    }
    PsiArrayType arrayType = (PsiArrayType)lastArgumentType;
    PsiType type = arrayType.getComponentType();
    PsiParameter[] parameters = parameterList.getParameters();
    PsiParameter lastParameter = parameters[parameters.length - 1];
    PsiEllipsisType lastParameterType =
      (PsiEllipsisType)lastParameter.getType();
    PsiType lastType = lastParameterType.getComponentType();
    JavaResolveResult resolveResult =
      methodCallExpression.resolveMethodGenerics();
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    PsiType substitutedType = substitutor.substitute(lastType);
    return !substitutedType.equals(type);
  }
}