/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

@ExtensionImpl
public class BoxingBoxedValueInspection extends BaseInspection {

  @NonNls
  static final Map<String, String> boxedPrimitiveMap =
    new HashMap<String, String>(8);

  static {
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_INTEGER, "int");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_SHORT, "short");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_BOOLEAN, "boolean");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_LONG, "long");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_BYTE, "byte");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_FLOAT, "float");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_DOUBLE, "double");
    boxedPrimitiveMap.put(CommonClassNames.JAVA_LANG_CHARACTER, "char");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.boxingBoxedValueDisplayName();
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.boxingBoxedValueProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new BoxingBoxedValueFix();
  }

  private static class BoxingBoxedValueFix extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.boxingBoxedValueQuickfix();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      PsiCallExpression parent = PsiTreeUtil.getParentOfType(
        element, PsiMethodCallExpression.class,
        PsiNewExpression.class);
      if (parent == null) {
        return;
      }
      parent.replace(element);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BoxingBoxedValueVisitor();
  }

  private static class BoxingBoxedValueVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
        return;
      }
      super.visitNewExpression(expression);
      PsiType constructorType = expression.getType();
      if (constructorType == null) {
        return;
      }
      String constructorTypeText =
        constructorType.getCanonicalText();
      if (!boxedPrimitiveMap.containsKey(constructorTypeText)) {
        return;
      }
      PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      PsiParameterList parameterList =
        constructor.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return;
      }
      PsiParameter[] parameters = parameterList.getParameters();
      PsiParameter parameter = parameters[0];
      PsiType parameterType = parameter.getType();
      String parameterTypeText = parameterType.getCanonicalText();
      String boxableConstructorType =
        boxedPrimitiveMap.get(constructorTypeText);
      if (!boxableConstructorType.equals(parameterTypeText)) {
        return;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      PsiExpression argument = arguments[0];
      PsiType argumentType = argument.getType();
      if (argumentType == null) {
        return;
      }
      String argumentTypeText = argumentType.getCanonicalText();
      if (!constructorTypeText.equals(argumentTypeText)) {
        return;
      }
      registerError(argument);
    }

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      if (!PsiUtil.isLanguageLevel5OrHigher(expression)) {
        return;
      }
      super.visitMethodCallExpression(expression);
      PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls String referenceName = methodExpression.getReferenceName();
      if (!"valueOf".equals(referenceName)) {
        return;
      }
      PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      String className = containingClass.getQualifiedName();
      if (className == null) {
        return;
      }
      if (!boxedPrimitiveMap.containsKey(className)) {
        return;
      }
      if (method.getParameterList().getParametersCount() != 1) {
        return;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      PsiExpression argument = arguments[0];
      PsiType argumentType = argument.getType();
      if (argumentType == null) {
        return;
      }
      String argumentTypeText = argumentType.getCanonicalText();
      if (!className.equals(argumentTypeText)) {
        return;
      }
      registerError(argument);
    }
  }
}
