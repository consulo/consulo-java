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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ChainedEqualityInspection extends BaseInspection {

  @Nonnull
  public String getID() {
    return "ChainedEqualityComparisons";
  }

  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.chainedEqualityComparisonsDisplayName();
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.chainedEqualityComparisonsProblemDescriptor().get();
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ChainedEqualityVisitor();
  }

  private static class ChainedEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiExpression) {
        if (ComparisonUtils.isEqualityComparison((PsiExpression)parent)) {
          return;
        }
      }
      final PsiExpression[] operands = expression.getOperands();
      if (operands.length >= 3) {
        registerError(expression);
      }
      else {
        for (PsiExpression operand : operands) {
          if (ComparisonUtils.isEqualityComparison(operand)) {
            registerError(expression);
            break;
          }
        }
      }
    }
  }
}