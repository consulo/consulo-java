/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.memory;

import com.intellij.java.language.psi.PsiArrayInitializerExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiNewExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ConstructionUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

public abstract class ZeroLengthArrayInitializationInspectionBase extends BaseInspection {
  @Pattern(VALID_ID_PATTERN)
  @Override
  @Nonnull
  public String getID() {
    return "ZeroLengthArrayAllocation";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.arrayAllocationZeroLengthDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.arrayAllocationZeroLengthProblemDescriptor().get();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ZeroLengthArrayInitializationVisitor();
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  private static class ZeroLengthArrayInitializationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@Nonnull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!ConstructionUtils.isEmptyArrayInitializer(expression)) {
        return;
      }
      if (ExpressionUtils.isDeclaredConstant(expression)) {
        return;
      }
      registerError(expression);
    }

    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      super.visitArrayInitializerExpression(expression);
      final PsiExpression[] initializers = expression.getInitializers();
      if (initializers.length > 0) {
        return;
      }
      if (expression.getParent() instanceof PsiNewExpression) {
        return;
      }
      if (ExpressionUtils.isDeclaredConstant(expression)) {
        return;
      }
      registerError(expression);
    }
  }
}
