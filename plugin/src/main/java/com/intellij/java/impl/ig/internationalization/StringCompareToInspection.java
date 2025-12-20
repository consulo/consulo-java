/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.analysis.impl.codeInsight.intention.AddAnnotationFix;
import com.intellij.java.impl.ig.DelegatingFix;
import com.intellij.java.language.codeInsight.AnnotationUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class StringCompareToInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "CallToStringCompareTo";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.stringComparetoCallDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.stringComparetoCallProblemDescriptor().get();
  }

  @Override
  @Nonnull
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    PsiMethodCallExpression methodCallExpression =
      (PsiMethodCallExpression)infos[0];
    List<InspectionGadgetsFix> result = new ArrayList();
    PsiReferenceExpression methodExpression =
      methodCallExpression.getMethodExpression();
    PsiModifierListOwner annotatableQualifier =
      NonNlsUtils.getAnnotatableQualifier(
        methodExpression);
    if (annotatableQualifier != null) {
      InspectionGadgetsFix fix = new DelegatingFix(
        new AddAnnotationFix(AnnotationUtil.NON_NLS,
                             annotatableQualifier));
      result.add(fix);
    }
    PsiModifierListOwner annotatableArgument =
      NonNlsUtils.getAnnotatableArgument(
        methodCallExpression);
    if (annotatableArgument != null) {
      InspectionGadgetsFix fix = new DelegatingFix(
        new AddAnnotationFix(AnnotationUtil.NON_NLS,
                             annotatableArgument));
      result.add(fix);
    }
    return result.toArray(new InspectionGadgetsFix[result.size()]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringCompareToVisitor();
  }

  private static class StringCompareToVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @Nonnull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isStringCompareTo(expression)) {
        return;
      }
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      PsiExpression qualifier =
        methodExpression.getQualifierExpression();
      if (NonNlsUtils.isNonNlsAnnotated(qualifier)) {
        return;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      if (NonNlsUtils.isNonNlsAnnotated(arguments[0])) {
        return;
      }
      registerMethodCallError(expression, expression);
    }

    private static boolean isStringCompareTo(
      PsiMethodCallExpression expression) {
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      String name = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.COMPARE_TO.equals(name)) {
        return false;
      }
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      if (!MethodUtils.isCompareTo(method)) {
        return false;
      }
      PsiParameterList parameterList = method.getParameterList();
      PsiParameter[] parameters = parameterList.getParameters();
      PsiType parameterType = parameters[0].getType();
      if (!TypeUtils.isJavaLangObject(parameterType) &&
          !TypeUtils.isJavaLangString(parameterType)) {
        return false;
      }
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      String className = aClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_STRING.equals(className);
    }
  }
}