/*
 * Copyright 2007-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class AssertEqualsBetweenInconvertibleTypesInspection extends BaseInspection {
  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.assertequalsBetweenInconvertibleTypesDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    PsiType comparedType = (PsiType)infos[0];
    PsiType comparisonType = (PsiType)infos[1];
    String comparedTypeText = comparedType.getPresentableText();
    String comparisonTypeText = comparisonType.getPresentableText();
    return InspectionGadgetsLocalize.assertequalsBetweenInconvertibleTypesProblemDescriptor(
      StringUtil.escapeXml(comparedTypeText),
      StringUtil.escapeXml(comparisonTypeText)
    ).get();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertEqualsBetweenInconvertibleTypesVisitor();
  }

  private static class AssertEqualsBetweenInconvertibleTypesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls String methodName = methodExpression.getReferenceName();
      if (!"assertEquals".equals(methodName)) {
        return;
      }
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert") &&
          !InheritanceUtil.isInheritor(containingClass, "org.junit.Assert")) {
        return;
      }
      PsiParameterList parameterList = method.getParameterList();
      PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length < 2) {
        return;
      }
      PsiType firstParameterType = parameters[0].getType();
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      int argumentIndex;
      if (firstParameterType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        if (arguments.length < 3) {
          return;
        }
        argumentIndex = 1;
      }
      else {
        if (arguments.length < 2) {
          return;
        }
        argumentIndex = 0;
      }
      PsiExpression expression1 = arguments[argumentIndex];
      PsiExpression expression2 = arguments[argumentIndex + 1];
      PsiType type1 = expression1.getType();
      if (type1 == null) {
        return;
      }
      PsiType type2 = expression2.getType();
      if (type2 == null) {
        return;
      }
      PsiType parameterType1 = parameters[argumentIndex].getType();
      PsiType parameterType2 = parameters[argumentIndex + 1].getType();
      PsiClassType objectType = TypeUtils.getObjectType(expression);
      if (!objectType.equals(parameterType1) || !objectType.equals(parameterType2)) {
        return;
      }
      if (TypeConversionUtil.areTypesConvertible(type1, type2)) {
        return;
      }
      registerMethodCallError(expression, type1, type2);
    }
  }
}
