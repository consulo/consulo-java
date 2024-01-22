/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.controlflow;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.language.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SimplifiableConditionalExpressionInspection
  extends BaseInspection {

  @Override
  @jakarta.annotation.Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "simplifiable.conditional.expression.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiConditionalExpression expression =
      (PsiConditionalExpression)infos[0];
    return InspectionGadgetsBundle.message(
      "simplifiable.conditional.expression.problem.descriptor",
      calculateReplacementExpression(expression));
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new SimplifiableConditionalFix();
  }

  private static class SimplifiableConditionalFix
    extends InspectionGadgetsFix {

    @jakarta.annotation.Nonnull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiConditionalExpression expression =
        (PsiConditionalExpression)descriptor.getPsiElement();
      final String newExpression =
        calculateReplacementExpression(expression);
      replaceExpression(expression, newExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableConditionalExpressionVisitor();
  }

  @NonNls
  static String calculateReplacementExpression(
    PsiConditionalExpression expression) {
    final PsiExpression thenExpression = expression.getThenExpression();
    final PsiExpression elseExpression = expression.getElseExpression();
    final PsiExpression condition = expression.getCondition();
    assert thenExpression != null;
    assert elseExpression != null;

    final String elseText = elseExpression.getText();
    String conditionText = condition.getText();
    if (BoolUtils.isTrue(thenExpression)) {
      @NonNls final String elseExpressionText;
      if (ParenthesesUtils.getPrecedence(elseExpression) >
          ParenthesesUtils.OR_PRECEDENCE) {
        elseExpressionText = '(' + elseText + ')';
      }
      else {
        elseExpressionText = elseText;
      }
      if (ParenthesesUtils.getPrecedence(condition) > ParenthesesUtils.OR_PRECEDENCE) {
        conditionText = "(" + conditionText + ")";
      }
      return conditionText + " || " + elseExpressionText;
    }
    else if (BoolUtils.isFalse(thenExpression)) {
      @NonNls final String elseExpressionText;
      if (ParenthesesUtils.getPrecedence(elseExpression) >
          ParenthesesUtils.AND_PRECEDENCE) {
        elseExpressionText = '(' + elseText + ')';
      }
      else {
        elseExpressionText = elseText;
      }
      return BoolUtils.getNegatedExpressionText(condition) + " && " +
             elseExpressionText;
    }
    final String thenText = thenExpression.getText();
    if (BoolUtils.isFalse(elseExpression)) {
      @NonNls final String thenExpressionText;
      if (ParenthesesUtils.getPrecedence(thenExpression) >
          ParenthesesUtils.AND_PRECEDENCE) {
        thenExpressionText = '(' + thenText + ')';
      }
      else {
        thenExpressionText = thenText;
      }
      if (ParenthesesUtils.getPrecedence(condition) > ParenthesesUtils.AND_PRECEDENCE) {
        conditionText = "(" + conditionText + ")";
      }
      return conditionText + " && " + thenExpressionText;
    }
    else {
      @NonNls final String thenExpressionText;
      if (ParenthesesUtils.getPrecedence(thenExpression) >
          ParenthesesUtils.OR_PRECEDENCE) {
        thenExpressionText = '(' + thenText + ')';
      }
      else {
        thenExpressionText = thenText;
      }
      return BoolUtils.getNegatedExpressionText(condition) + " || " +
             thenExpressionText;
    }
  }

  private static class SimplifiableConditionalExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(
      PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression thenExpression = expression.getThenExpression();
      if (thenExpression == null) {
        return;
      }
      final PsiType thenType = thenExpression.getType();
      if (!PsiType.BOOLEAN.equals(thenType)) {
        return;
      }
      final PsiExpression elseExpression = expression.getElseExpression();
      if (elseExpression == null) {
        return;
      }
      final PsiType elseType = elseExpression.getType();
      if (!PsiType.BOOLEAN.equals(elseType)) {
        return;
      }
      final boolean thenConstant = BoolUtils.isFalse(thenExpression) ||
                                   BoolUtils.isTrue(thenExpression);
      final boolean elseConstant = BoolUtils.isFalse(elseExpression) ||
                                   BoolUtils.isTrue(elseExpression);
      if (thenConstant == elseConstant) {
        return;
      }
      registerError(expression, expression);
    }
  }
}