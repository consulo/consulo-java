/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.assignment;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class ReplaceAssignmentWithOperatorAssignmentInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignoreLazyOperators = true;

  /**
   * @noinspection PublicField
   */
  public boolean ignoreObscureOperators = false;

  @Override
  @Nonnull
  public String getID() {
    return "AssignmentReplaceableWithOperatorAssignment";
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.assignmentReplaceableWithOperatorAssignmentDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiExpression lhs = (PsiExpression)infos[0];
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)infos[1];
    return InspectionGadgetsLocalize.assignmentReplaceableWithOperatorAssignmentProblemDescriptor(
        calculateReplacementExpression(lhs, polyadicExpression)
    ).get();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.assignmentReplaceableWithOperatorAssignmentIgnoreConditionalOperatorsOption().get(),
      "ignoreLazyOperators"
    );
    optionsPanel.addCheckbox(
      InspectionGadgetsLocalize.assignmentReplaceableWithOperatorAssignmentIgnoreObscureOperatorsOption().get(),
      "ignoreObscureOperators"
    );
    return optionsPanel;
  }

  static String calculateReplacementExpression(PsiExpression lhs, PsiPolyadicExpression polyadicExpression) {
    final PsiExpression[] operands = polyadicExpression.getOperands();
    final PsiJavaToken sign = polyadicExpression.getTokenBeforeOperand(operands[1]);
    String signText = sign.getText();
    if ("&&".equals(signText)) {
      signText = "&";
    }
    else if ("||".equals(signText)) {
      signText = "|";
    }
    final StringBuilder text = new StringBuilder(lhs.getText());
    text.append(' ');
    text.append(signText);
    text.append("= ");
    boolean addToken = false;
    for (int i = 1; i < operands.length; i++) {
      final PsiExpression operand = operands[i];
      if (addToken) {
        final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
        text.append(' ');
        if (token != null) {
          text.append(token.getText());
        }
        text.append(' ');
      }
      else {
        addToken = true;
      }
      text.append(operand.getText());
    }
    return text.toString();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceAssignmentWithOperatorAssignmentFix((PsiPolyadicExpression)infos[1]);
  }

  private static class ReplaceAssignmentWithOperatorAssignmentFix extends InspectionGadgetsFix {

    private final String m_name;

    private ReplaceAssignmentWithOperatorAssignmentFix(PsiPolyadicExpression expression) {
      final PsiJavaToken sign = expression.getTokenBeforeOperand(expression.getOperands()[1]);
      String signText = sign.getText();
      if ("&&".equals(signText)) {
        signText = "&";
      }
      else if ("||".equals(signText)) {
        signText = "|";
      }
      m_name = InspectionGadgetsLocalize.assignmentReplaceableWithOperatorReplaceQuickfix(signText).get();
    }

    @Nonnull
    public String getName() {
      return m_name;
    }

    @Override
    public void doFix(@Nonnull Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiAssignmentExpression)) {
        return;
      }
      final PsiAssignmentExpression expression = (PsiAssignmentExpression)element;
      final PsiExpression lhs = expression.getLExpression();
      PsiExpression rhs = ParenthesesUtils.stripParentheses(expression.getRExpression());
      if (rhs instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)rhs;
        final PsiType castType = typeCastExpression.getType();
        if (castType == null || !castType.equals(lhs.getType())) {
          return;
        }
        rhs = ParenthesesUtils.stripParentheses(typeCastExpression.getOperand());
      }
      if (!(rhs instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)rhs;
      final String newExpression = calculateReplacementExpression(lhs, polyadicExpression);
      replaceExpression(expression, newExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReplaceAssignmentWithOperatorAssignmentVisitor();
  }

  private class ReplaceAssignmentWithOperatorAssignmentVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);
      final IElementType assignmentTokenType = assignment.getOperationTokenType();
      if (!assignmentTokenType.equals(JavaTokenType.EQ)) {
        return;
      }
      final PsiExpression lhs = assignment.getLExpression();
      PsiExpression rhs = ParenthesesUtils.stripParentheses(assignment.getRExpression());
      if (rhs instanceof PsiTypeCastExpression) {
        final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)rhs;
        final PsiType castType = typeCastExpression.getType();
        if (castType == null || !castType.equals(lhs.getType())) {
          return;
        }
        rhs = ParenthesesUtils.stripParentheses(typeCastExpression.getOperand());
      }
      if (!(rhs instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)rhs;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (operands.length < 2) {
        return;
      }
      if (operands.length > 2 && !ParenthesesUtils.isAssociativeOperation(polyadicExpression)) {
        return;
      }
      for (PsiExpression operand : operands) {
        if (operand == null) {
          return;
        }
      }
      final IElementType expressionTokenType = polyadicExpression.getOperationTokenType();
      if (JavaTokenType.EQEQ.equals(expressionTokenType) || JavaTokenType.NE.equals(expressionTokenType)) {
        return;
      }
      if (ignoreLazyOperators) {
        if (JavaTokenType.ANDAND.equals(expressionTokenType) || JavaTokenType.OROR.equals(expressionTokenType)) {
          return;
        }
      }
      if (ignoreObscureOperators) {
        if (JavaTokenType.XOR.equals(expressionTokenType) || JavaTokenType.PERC.equals(expressionTokenType)) {
          return;
        }
      }
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(lhs, operands[0])) {
        return;
      }
      registerError(assignment, lhs, polyadicExpression);
    }
  }
}