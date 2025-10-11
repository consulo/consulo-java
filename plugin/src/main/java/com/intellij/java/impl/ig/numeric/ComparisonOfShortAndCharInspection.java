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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ComparisonOfShortAndCharInspection extends BaseInspection {

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.comparisonOfShortAndCharDisplayName();
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.comparisonOfShortAndCharProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ComparisonOfShortAndCharVisitor();
  }

  private static class ComparisonOfShortAndCharVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@Nonnull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiType lhsType = lhs.getType();
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      final PsiType rhsType = rhs.getType();
      if (PsiType.SHORT.equals(lhsType) && PsiType.CHAR.equals(rhsType)) {
        registerError(expression);
      }
      else if (PsiType.CHAR.equals(lhsType) && PsiType.SHORT.equals(rhsType)) {
        registerError(expression);
      }
    }
  }
}
