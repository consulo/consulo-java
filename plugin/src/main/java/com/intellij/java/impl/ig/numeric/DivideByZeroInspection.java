/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class DivideByZeroInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "divzero";
  }

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.divideByZeroDisplayName().get();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.divideByZeroProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new DivisionByZeroVisitor();
  }

  private static class DivisionByZeroVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.DIV.equals(tokenType) && !JavaTokenType.PERC.equals(tokenType)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression operand = operands[i];
        if (isZero(operand)) {
          registerError(operand);
          return;
        }
      }
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression rhs = expression.getRExpression();
      if (rhs == null) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.DIVEQ) && !tokenType.equals(JavaTokenType.PERCEQ) || isZero(rhs)) {
        return;
      }
      registerError(expression);
    }

    private static boolean isZero(PsiExpression expression) {
      final Object value = ConstantExpressionUtil.computeCastTo(expression, PsiType.DOUBLE);
      if (!(value instanceof Double)) {
        return false;
      }
      final double constantValue = ((Double)value).doubleValue();
      return constantValue == 0.0 || constantValue == -0.0;
    }
  }
}