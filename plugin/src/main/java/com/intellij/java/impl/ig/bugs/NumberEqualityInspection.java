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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.impl.ig.fixes.EqualityToEqualsFix;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiKeyword;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.java.language.module.util.JavaClassNames;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class NumberEqualityInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.numberComparisonDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.numberComparisonProblemDescriptor().get();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NumberEqualityVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new EqualityToEqualsFix();
  }

  private static class NumberEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @Nonnull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      if (!hasNumberType(lhs) || !hasNumberType(rhs)) {
        return;
      }
      final String lhsText = lhs.getText();
      if (PsiKeyword.NULL.equals(lhsText)) {
        return;
      }
      final String rhsText = rhs.getText();
      if (PsiKeyword.NULL.equals(rhsText)) {
        return;
      }
      final PsiJavaToken sign = expression.getOperationSign();
      registerError(sign);
    }

    private static boolean hasNumberType(PsiExpression expression) {
      return TypeUtils.expressionHasTypeOrSubtype(expression,
                                                  JavaClassNames.JAVA_LANG_NUMBER);
    }
  }
}