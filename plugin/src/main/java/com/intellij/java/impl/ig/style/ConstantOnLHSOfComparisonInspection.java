/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ConstantOnLHSOfComparisonInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getID() {
    return "ConstantOnLeftSideOfComparison";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.constantOnLhsOfComparisonDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.constantOnLhsOfComparisonProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantOnLHSOfComparisonVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new SwapComparisonFix();
  }

  private static class SwapComparisonFix extends InspectionGadgetsFix {

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.flipComparisonQuickfix();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiBinaryExpression expression = (PsiBinaryExpression)descriptor.getPsiElement();
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      final String flippedComparison = ComparisonUtils.getFlippedComparison(expression.getOperationTokenType());
      if (flippedComparison == null) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final String rhsText = rhs.getText();
      final String lhsText = lhs.getText();
      replaceExpression(expression, rhsText + ' ' + flippedComparison + ' ' + lhsText);
    }
  }

  private static class ConstantOnLHSOfComparisonVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@Nonnull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null || !isConstantExpression(lhs) || isConstantExpression(rhs)) {
        return;
      }
      registerError(expression);
    }

    private static boolean isConstantExpression(PsiExpression expression) {
      return ExpressionUtils.isNullLiteral(expression) || PsiUtil.isConstantExpression(expression);
    }
  }
}