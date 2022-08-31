/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.java.language.psi.PsiArrayInitializerExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import javax.annotation.Nonnull;

import javax.swing.*;

public class OverlyLargePrimitiveArrayInitializerInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public int m_limit = 64;

  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "large.initializer.primitive.type.array.display.name");
  }

  @Nonnull
  public String buildErrorString(Object... infos) {
    final Integer numElements = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "large.initializer.primitive.type.array.problem.descriptor",
      numElements);
  }

  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(
      InspectionGadgetsBundle.message(
        "large.initializer.primitive.type.array.maximum.number.of.elements.option"),
      this, "m_limit");
  }

  public BaseInspectionVisitor buildVisitor() {
    return new OverlyLargePrimitiveArrayInitializerVisitor();
  }

  private class OverlyLargePrimitiveArrayInitializerVisitor
    extends BaseInspectionVisitor {


    @Override
    public void visitArrayInitializerExpression(
      PsiArrayInitializerExpression expression) {
      super.visitArrayInitializerExpression(expression);
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      final PsiType componentType = type.getDeepComponentType();
      if (!(componentType instanceof PsiPrimitiveType)) {
        return;
      }
      final int numElements = calculateNumElements(expression);
      if (numElements <= m_limit) {
        return;
      }
      registerError(expression, Integer.valueOf(numElements));
    }

    private int calculateNumElements(PsiExpression expression) {
      if (expression instanceof PsiArrayInitializerExpression) {
        final PsiArrayInitializerExpression arrayExpression =
          (PsiArrayInitializerExpression)expression;
        final PsiExpression[] initializers =
          arrayExpression.getInitializers();
        int out = 0;
        for (final PsiExpression initializer : initializers) {
          out += calculateNumElements(initializer);
        }
        return out;
      }
      return 1;
    }
  }
}