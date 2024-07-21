/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.j2me;

import com.intellij.java.impl.ig.psiutils.WellFormednessUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class MultiplyOrDivideByPowerOfTwoInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean checkDivision = false;

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.multiplyOrDivideByPowerOfTwoDisplayName().get();
  }

  @Nullable
  public JComponent createOptionsPanel() {
    LocalizeValue message = InspectionGadgetsLocalize.multiplyOrDivideByPowerOfTwoDivideOption();
    return new SingleCheckboxOptionsPanel(message.get(), this, "checkDivision");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.expressionCanBeReplacedProblemDescriptor(calculateReplacementShift((PsiExpression)infos[0])).get();
  }

  static String calculateReplacementShift(PsiExpression expression) {
    final PsiExpression lhs;
    final PsiExpression rhs;
    final String operator;
    if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression exp = (PsiAssignmentExpression)expression;
      lhs = exp.getLExpression();
      rhs = exp.getRExpression();
      final IElementType tokenType = exp.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISKEQ)) {
        operator = "<<=";
      }
      else {
        operator = ">>=";
      }
    }
    else {
      final PsiBinaryExpression exp = (PsiBinaryExpression)expression;
      lhs = exp.getLOperand();
      rhs = exp.getROperand();
      final IElementType tokenType = exp.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISK)) {
        operator = "<<";
      }
      else {
        operator = ">>";
      }
    }
    final String lhsText;
    if (ParenthesesUtils.getPrecedence(lhs) >
        ParenthesesUtils.SHIFT_PRECEDENCE) {
      lhsText = '(' + lhs.getText() + ')';
    }
    else {
      lhsText = lhs.getText();
    }
    String expString =
      lhsText + operator + ShiftUtils.getLogBaseTwo(rhs);
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpression) {
      if (!(parent instanceof PsiParenthesizedExpression) &&
          ParenthesesUtils.getPrecedence((PsiExpression)parent) <
          ParenthesesUtils.SHIFT_PRECEDENCE) {
        expString = '(' + expString + ')';
      }
    }
    return expString;
  }

  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    if (expression instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)expression;
      final IElementType operationTokenType =
        binaryExpression.getOperationTokenType();
      if (JavaTokenType.DIV.equals(operationTokenType)) {
        return null;
      }
    }
    else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression =
        (PsiAssignmentExpression)expression;
      final IElementType operationTokenType =
        assignmentExpression.getOperationTokenType();
      if (JavaTokenType.DIVEQ.equals(operationTokenType)) {
        return null;
      }
    }
    return new MultiplyByPowerOfTwoFix();
  }

  private static class MultiplyByPowerOfTwoFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.multiplyOrDivideByPowerOfTwoReplaceQuickfix().get();
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiExpression expression = (PsiExpression)descriptor.getPsiElement();
      final String newExpression = calculateReplacementShift(expression);
      replaceExpression(expression, newExpression);
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ConstantShiftVisitor();
  }

  private class ConstantShiftVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @Nonnull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }

      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.ASTERISK)) {
        if (!checkDivision || !tokenType.equals(JavaTokenType.DIV)) {
          return;
        }
      }
      if (!ShiftUtils.isPowerOfTwo(rhs)) {
        return;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      if (!ClassUtils.isIntegral(type)) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitAssignmentExpression(
      @Nonnull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (!WellFormednessUtils.isWellFormed(expression)) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.ASTERISKEQ)) {
        if (!checkDivision || !tokenType.equals(JavaTokenType.DIVEQ)) {
          return;
        }
      }
      final PsiExpression rhs = expression.getRExpression();
      if (!ShiftUtils.isPowerOfTwo(rhs)) {
        return;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      if (!ClassUtils.isIntegral(type)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}