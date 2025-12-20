/*
 * Copyright 2010-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class StringConcatenationInMessageFormatCallInspection extends BaseInspection {
  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.stringConcatenationInMessageFormatCallDisplayName();
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.stringConcatenationInMessageFormatCallProblemDescriptor().get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    PsiReferenceExpression referenceExpression = (PsiReferenceExpression)infos[0];
    String referenceName = referenceExpression.getReferenceName();
    return new StringConcatenationInFormatCallFix(referenceName);
  }

  private static class StringConcatenationInFormatCallFix extends InspectionGadgetsFix {

    private final String variableName;

    public StringConcatenationInFormatCallFix(String variableName) {
      this.variableName = variableName;
    }

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.stringConcatenationInFormatCallQuickfix();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiBinaryExpression)) {
        return;
      }
      PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
      PsiElement parent = binaryExpression.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return;
      }
      PsiExpressionList expressionList = (PsiExpressionList)parent;
      PsiExpression lhs = binaryExpression.getLOperand();
      PsiExpression rhs = binaryExpression.getROperand();
      if (rhs == null) {
        return;
      }
      PsiExpression[] expressions = expressionList.getExpressions();
      int parameter = expressions.length - 1;
      expressionList.add(rhs);
      Object constant =
        ExpressionUtils.computeConstantExpression(lhs);
      if (constant instanceof String) {
        PsiExpression newExpression = addParameter(lhs, parameter);
        if (newExpression == null) {
          expressionList.addAfter(lhs, binaryExpression);
        }
        else {
          expressionList.addAfter(newExpression, binaryExpression);
        }
      }
      else {
        expressionList.addAfter(lhs, binaryExpression);
      }
      binaryExpression.delete();
    }

    @Nullable
    private static PsiExpression addParameter(PsiExpression expression, int parameterNumber) {
      if (expression instanceof PsiBinaryExpression) {
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
        PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
          return null;
        }
        PsiExpression newExpression = addParameter(rhs, parameterNumber);
        if (newExpression == null) {
          return null;
        }
        rhs.replace(newExpression);
        return expression;
      }
      else if (expression instanceof PsiLiteralExpression) {
        PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
        Object value = literalExpression.getValue();
        if (!(value instanceof String)) {
          return null;
        }
        Project project = expression.getProject();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        return factory.createExpressionFromText("\"" + value + '{' + parameterNumber + "}\"", null);
      }
      else {
        return null;
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInMessageFormatCallVisitor();
  }

  private static class StringConcatenationInMessageFormatCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isMessageFormatCall(expression)) {
        return;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length == 0) {
        return;
      }
      PsiExpression firstArgument = arguments[0];
      PsiType type = firstArgument.getType();
      if (type == null) {
        return;
      }
      int formatArgumentIndex;
      if ("java.util.Locale".equals(type.getCanonicalText()) && arguments.length > 1) {
        formatArgumentIndex = 1;
      }
      else {
        formatArgumentIndex = 0;
      }
      PsiExpression formatArgument = arguments[formatArgumentIndex];
      PsiType formatArgumentType = formatArgument.getType();
      if (formatArgumentType == null || !formatArgumentType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      if (!(formatArgument instanceof PsiBinaryExpression)) {
        return;
      }
      if (PsiUtil.isConstantExpression(formatArgument)) {
        return;
      }
      PsiBinaryExpression binaryExpression = (PsiBinaryExpression)formatArgument;
      PsiExpression lhs = binaryExpression.getLOperand();
      PsiType lhsType = lhs.getType();
      if (lhsType == null || !lhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return;
      }
      PsiExpression rhs = binaryExpression.getROperand();
      if (!(rhs instanceof PsiReferenceExpression)) {
        return;
      }
      registerError(formatArgument, rhs);
    }

    private static boolean isMessageFormatCall(PsiMethodCallExpression expression) {
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls String referenceName = methodExpression.getReferenceName();
      if (!"format".equals(referenceName)) {
        return false;
      }
      PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (!(qualifierExpression instanceof PsiReferenceExpression)) {
        return false;
      }
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
      PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiClass)) {
        return false;
      }
      PsiClass aClass = (PsiClass)target;
      return InheritanceUtil.isInheritor(aClass, "java.text.MessageFormat");
    }
  }
}
