/*
 * Copyright 2006-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class BadOddnessInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.badOddnessDisplayName();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.badOddnessProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new BadOddnessVisitor();
  }

  private static class BadOddnessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @Nonnull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (expression.getROperand() == null) {
        return;
      }
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      PsiExpression lhs = expression.getLOperand();
      PsiExpression rhs = expression.getROperand();
      if (isModTwo(lhs) && hasValue(rhs, 1)) {
        registerError(expression, expression);
      }
      if (isModTwo(rhs) && hasValue(lhs, 1)) {
        registerError(expression, expression);
      }
    }

    private static boolean isModTwo(PsiExpression exp) {
      if (!(exp instanceof PsiBinaryExpression)) {
        return false;
      }
      PsiBinaryExpression binary = (PsiBinaryExpression)exp;
      IElementType tokenType = binary.getOperationTokenType();
      if (!JavaTokenType.PERC.equals(tokenType)) {
        return false;
      }
      PsiExpression rhs = binary.getROperand();
      PsiExpression lhs = binary.getLOperand();
      if (rhs == null) {
        return false;
      }
      return hasValue(rhs, 2) || hasValue(lhs, 2);
    }

    private static boolean hasValue(PsiExpression expression, int testValue) {
      Integer value = (Integer)
        ConstantExpressionUtil.computeCastTo(
          expression, PsiType.INT);
      return value != null && value.intValue() == testValue;
    }
  }
}