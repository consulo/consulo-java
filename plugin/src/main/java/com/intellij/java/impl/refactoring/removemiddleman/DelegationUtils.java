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
package com.intellij.java.impl.refactoring.removemiddleman;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.util.collection.ArrayUtil;

import java.util.HashSet;
import java.util.Set;

public class DelegationUtils {
  private DelegationUtils() {
    super();
  }



  public static Set<PsiMethod> getDelegatingMethodsForField(PsiField field) {
    Set<PsiMethod> out = new HashSet<PsiMethod>();
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) {
      return out;
    }
    PsiMethod[] methods = containingClass.getMethods();
    for (PsiMethod method : methods) {
      if (isDelegation(field, method)) {
        out.add(method);
      }
    }
    return out;
  }

  private static boolean isDelegation(PsiField field, PsiMethod method) {
    if (method.isConstructor()) {
      return false;
    }
    PsiCodeBlock body = method.getBody();
    if (body == null) {
      return false;
    }
    PsiStatement[] statements = body.getStatements();
    if (statements.length != 1) {
      return false;
    }
    PsiStatement statement = statements[0];
    if (statement instanceof PsiReturnStatement) {
      PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
      if (!isDelegationCall(returnValue, field, method)) {
        return false;
      }
    }
    else if (statement instanceof PsiExpressionStatement) {
      PsiExpression value = ((PsiExpressionStatement)statement).getExpression();
      if (!isDelegationCall(value, field, method)) {
        return false;
      }
    }
    else {
      return false;
    }

    return true;
  }

  public static boolean isAbstract(PsiMethod method) {
    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return true;
    }
    return method.getContainingClass().isInterface();
  }

  private static boolean isDelegationCall(PsiExpression expression, PsiField field, PsiMethod method) {
    if (!(expression instanceof PsiMethodCallExpression)) {
      return false;
    }
    PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiElement referent = ((PsiReference)qualifier).resolve();
    if (referent == null || !referent.equals(field)) {
      return false;
    }
    PsiExpressionList argumentList = call.getArgumentList();
    PsiExpression[] args = argumentList.getExpressions();
    for (PsiExpression arg : args) {
      if (!isParameterReference(arg, method)) {
        return false;
      }
    }
    return true;
  }

  private static boolean isParameterReference(PsiExpression arg, PsiMethod method) {
    if (!(arg instanceof PsiReferenceExpression)) {
      return false;
    }
    PsiElement referent = ((PsiReference)arg).resolve();
    if (!(referent instanceof PsiParameter)) {
      return false;
    }
    PsiElement declarationScope = ((PsiParameter)referent).getDeclarationScope();
    return method.equals(declarationScope);
  }


  public static int[] getParameterPermutation(PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    assert body != null;
    PsiStatement[] statements = body.getStatements();
    PsiStatement statement = statements[0];
    PsiParameterList parameterList = method.getParameterList();
    if (statement instanceof PsiReturnStatement) {
      PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
      PsiMethodCallExpression call = (PsiMethodCallExpression)returnValue;
      return calculatePermutation(call, parameterList);
    }
    else {
      PsiExpression value = ((PsiExpressionStatement)statement).getExpression();
      PsiMethodCallExpression call = (PsiMethodCallExpression)value;
      return calculatePermutation(call, parameterList);
    }
  }

  private static int[] calculatePermutation(PsiMethodCallExpression call, PsiParameterList parameterList) {
    PsiExpressionList argumentList = call.getArgumentList();
    PsiExpression[] args = argumentList.getExpressions();
    int[] out = ArrayUtil.newIntArray(args.length);
    for (int i = 0; i < args.length; i++) {
      PsiExpression arg = args[i];
      PsiParameter parameter = (PsiParameter)((PsiReference)arg).resolve();
      out[i] = parameterList.getParameterIndex(parameter);
    }
    return out;
  }

  public static PsiMethod getDelegatedMethod(PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    assert body != null;
    PsiStatement[] statements = body.getStatements();
    PsiStatement statement = statements[0];
    if (statement instanceof PsiReturnStatement) {
      PsiExpression returnValue = ((PsiReturnStatement)statement).getReturnValue();
      PsiMethodCallExpression call = (PsiMethodCallExpression)returnValue;
      assert call != null;
      return call.resolveMethod();
    }
    else {
      PsiExpression value = ((PsiExpressionStatement)statement).getExpression();
      PsiMethodCallExpression call = (PsiMethodCallExpression)value;
      return call.resolveMethod();
    }
  }
}
