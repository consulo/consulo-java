/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.ipp.concatenation;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import jakarta.annotation.Nullable;

/**
 * @author Bas Leijdekkers
 */
class CallSequencePredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiExpressionStatement)) {
      return false;
    }
    PsiStatement statement = (PsiStatement)element;
    PsiStatement nextSibling = PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class);
    if (nextSibling == null) {
      return false;
    }
    PsiVariable variable1 = getVariable(statement);
    if (variable1 == null) {
      return false;
    }
    PsiVariable variable2 = getVariable(nextSibling);
    return variable1.equals(variable2);
  }

  @Nullable
  private static PsiVariable getVariable(PsiStatement statement) {
    if (!(statement instanceof PsiExpressionStatement)) {
      return null;
    }
    PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
    PsiExpression expression = expressionStatement.getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) {
      return null;
    }
    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
    return getVariable(methodCallExpression);
}
 @Nullable
 private static PsiVariable getVariable(PsiMethodCallExpression methodCallExpression) {
   PsiType type = methodCallExpression.getType();
   if (!(type instanceof PsiClassType)) {
     return null;
   }
   PsiClassType classType = (PsiClassType)type;
   PsiClass aClass = classType.resolve();
   if (aClass == null) {
     return null;
   }
   PsiMethod method = methodCallExpression.resolveMethod();
   if (method == null) {
     return null;
   }
   PsiClass containingClass = method.getContainingClass();
   if (!aClass.equals(containingClass)) {
     return null;
   }
   PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
   PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
   if (qualifierExpression instanceof PsiMethodCallExpression) {
     PsiMethodCallExpression expression = (PsiMethodCallExpression)qualifierExpression;
     return getVariable(expression);
   } else if (!(qualifierExpression instanceof PsiReferenceExpression)) {
     return null;
   }
     PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
   PsiElement target = referenceExpression.resolve();
   if (!(target instanceof PsiVariable)) {
     return null;
   }
   return (PsiVariable)target;
 }
}
