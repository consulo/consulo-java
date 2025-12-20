/*
 * Copyright 2007-2010 Bas Leijdekkers
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
package com.intellij.java.impl.ig.internationalization;

import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import consulo.util.dataholder.Key;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nullable;

public class NonNlsUtils {

  private static final Key<Boolean> KEY = new Key("IG_NON_NLS_ANNOTATED_USE");

  private NonNlsUtils() {
  }

  @Nullable
  public static PsiModifierListOwner getAnnotatableArgument(
    PsiMethodCallExpression methodCallExpression) {
    PsiExpressionList argumentList =
      methodCallExpression.getArgumentList();
    PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length < 1) {
      return null;
    }
    PsiExpression argument = arguments[0];
    if (argument instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)argument;
      PsiElement element = referenceExpression.resolve();
      if (element instanceof PsiModifierListOwner) {
        return (PsiModifierListOwner)element;
      }
    }
    return null;
  }

  @Nullable
  public static PsiModifierListOwner getAnnotatableQualifier(
    PsiReferenceExpression expression) {
    PsiExpression qualifierExpression =
      expression.getQualifierExpression();
    if (qualifierExpression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)qualifierExpression;
      PsiElement element = referenceExpression.resolve();
      if (element instanceof PsiModifierListOwner) {
        return (PsiModifierListOwner)element;
      }
    }
    return null;
  }

  public static boolean isNonNlsAnnotated(
    @Nullable PsiExpression expression) {
    if (isReferenceToNonNlsAnnotatedElement(expression)) {
      return true;
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)expression;
      PsiMethod method = methodCallExpression.resolveMethod();
      if (isNonNlsAnnotatedModifierListOwner(method)) {
        return true;
      }
      PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      return isNonNlsAnnotated(qualifier);
    }
    else if (expression instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression arrayAccessExpression =
        (PsiArrayAccessExpression)expression;
      PsiExpression arrayExpression =
        arrayAccessExpression.getArrayExpression();
      return isNonNlsAnnotated(arrayExpression);
    }
    return false;
  }

  public static boolean isNonNlsAnnotatedUse(
    @Nullable PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    Boolean value = getCachedValue(expression, KEY);
    if (value != null) {
      return value.booleanValue();
    }
    PsiElement element =
      PsiTreeUtil.getParentOfType(expression,
                                  PsiExpressionList.class,
                                  PsiAssignmentExpression.class,
                                  PsiVariable.class,
                                  PsiReturnStatement.class);
    boolean result;
    if (element instanceof PsiExpressionList) {
      PsiExpressionList expressionList =
        (PsiExpressionList)element;
      result = isNonNlsAnnotatedParameter(expression, expressionList);
    }
    else if (element instanceof PsiVariable) {
      result = isNonNlsAnnotatedModifierListOwner(element);
    }
    else if (element instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)element;
      result =
        isAssignmentToNonNlsAnnotatedVariable(assignmentExpression);
    }
    else if (element instanceof PsiReturnStatement) {
      PsiMethod method =
        PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      result = isNonNlsAnnotatedModifierListOwner(method);
    }
    else {
      result = false;
    }
    putCachedValue(expression, KEY, Boolean.valueOf(result));
    return result;
  }

  private static <T> void putCachedValue(PsiExpression expression,
                                         Key<T> key, T value) {
    if (expression instanceof PsiBinaryExpression) {
      expression.putUserData(key, value);
    }
  }

  @Nullable
  private static <T> T getCachedValue(PsiExpression expression, Key<T> key) {
    T data = expression.getUserData(key);
    if (!(expression instanceof PsiBinaryExpression)) {
      return data;
    }
    PsiBinaryExpression binaryExpression =
      (PsiBinaryExpression)expression;
    PsiExpression lhs = binaryExpression.getLOperand();
    T childData = null;
    if (lhs instanceof PsiBinaryExpression) {
      childData = lhs.getUserData(key);
    }
    if (childData == null) {
      PsiExpression rhs = binaryExpression.getROperand();
      if (rhs instanceof PsiBinaryExpression) {
        childData = rhs.getUserData(key);
      }
    }
    if (childData != data) {
      expression.putUserData(key, childData);
    }
    return childData;
  }

  private static boolean isAssignmentToNonNlsAnnotatedVariable(
    PsiAssignmentExpression assignmentExpression) {
    PsiExpression lhs = assignmentExpression.getLExpression();
    return isReferenceToNonNlsAnnotatedElement(lhs);
  }

  private static boolean isReferenceToNonNlsAnnotatedElement(
    @Nullable PsiExpression expression) {
    if (!(expression instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)expression;
    PsiElement target = referenceExpression.resolve();
    return isNonNlsAnnotatedModifierListOwner(target);
  }

  private static boolean isNonNlsAnnotatedParameter(
    PsiExpression expression,
    PsiExpressionList expressionList) {
    PsiElement parent = expressionList.getParent();
    PsiParameterList parameterList;
    if (parent instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)parent;
      if (isQualifierNonNlsAnnotated(methodCallExpression)) {
        return true;
      }
      PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      parameterList = method.getParameterList();
    }
    else if (parent instanceof PsiNewExpression) {
      PsiNewExpression newExpression = (PsiNewExpression)parent;
      PsiMethod constructor = newExpression.resolveConstructor();
      if (constructor == null) {
        return false;
      }
      parameterList = constructor.getParameterList();
    }
    else {
      return false;
    }
    PsiExpression[] expressions = expressionList.getExpressions();
    int index = -1;
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression argument = expressions[i];
      if (PsiTreeUtil.isAncestor(argument, expression, false)) {
        index = i;
      }
    }
    PsiParameter[] parameters = parameterList.getParameters();
    if (parameters.length == 0) {
      return false;
    }
    PsiParameter parameter;
    if (index < parameters.length) {
      parameter = parameters[index];
    }
    else {
      parameter = parameters[parameters.length - 1];
    }
    return isNonNlsAnnotatedModifierListOwner(parameter);
  }

  private static boolean isQualifierNonNlsAnnotated(
    PsiMethodCallExpression methodCallExpression) {
    PsiReferenceExpression methodExpression =
      methodCallExpression.getMethodExpression();
    PsiExpression qualifier =
      methodExpression.getQualifierExpression();
    if (isReferenceToNonNlsAnnotatedElement(qualifier)) {
      return true;
    }
    if (qualifier instanceof PsiMethodCallExpression) {
      PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      if (isChainable(method)) {
        PsiMethodCallExpression expression =
          (PsiMethodCallExpression)qualifier;
        if (isQualifierNonNlsAnnotated(expression)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isChainable(PsiMethod method) {
    if (method == null) {
      return false;
    }
    method = (PsiMethod)method.getNavigationElement();
    PsiCodeBlock body = method.getBody();
    if (body == null) {
      return false;
    }
    PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      return false;
    }
    PsiStatement lastStatement = statements[statements.length - 1];
    if (!(lastStatement instanceof PsiReturnStatement)) {
      return false;
    }
    PsiReturnStatement returnStatement =
      (PsiReturnStatement)lastStatement;
    PsiExpression returnValue = returnStatement.getReturnValue();
    return returnValue instanceof PsiThisExpression;
  }

  private static boolean isNonNlsAnnotatedModifierListOwner(
    @Nullable PsiElement element) {
    if (!(element instanceof PsiModifierListOwner)) {
      return false;
    }
    PsiModifierListOwner variable = (PsiModifierListOwner)element;
    return AnnotationUtil.isAnnotated(variable,
                                      AnnotationUtil.NON_NLS, false, false);
  }
}