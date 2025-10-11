/*
 * Copyright 2009-2010 Bas Leijdekkers
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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class IntLiteralMayBeLongLiteralInspection extends BaseInspection {

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.intLiteralMayBeLongLiteralDisplayName();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)infos[0];
    final StringBuilder replacementText = buildReplacementText(typeCastExpression, new StringBuilder());
    return InspectionGadgetsLocalize.intLiteralMayBeLongLiteralProblemDescriptor(replacementText).get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)infos[0];
    final StringBuilder replacementText = buildReplacementText(typeCastExpression, new StringBuilder());
    return new IntLiteralMayBeLongLiteralFix(replacementText.toString());
  }

  private static StringBuilder buildReplacementText(
    PsiExpression expression, StringBuilder out) {
    if (expression instanceof PsiLiteralExpression) {
      out.append(expression.getText());
      out.append('L');
    }
    else if (expression instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression =
        (PsiPrefixExpression)expression;
      final PsiJavaToken sign = prefixExpression.getOperationSign();
      out.append(sign.getText());
      return buildReplacementText(prefixExpression.getOperand(), out);
    }
    else if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression =
        (PsiParenthesizedExpression)expression;
      out.append('(');
      buildReplacementText(parenthesizedExpression.getExpression(),
                           out);
      out.append(')');
    }
    else if (expression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCastExpression =
        (PsiTypeCastExpression)expression;
      final PsiExpression operand = typeCastExpression.getOperand();
      buildReplacementText(operand, out);
    }
    else {
      assert false;
    }
    return out;
  }

  private static class IntLiteralMayBeLongLiteralFix
    extends InspectionGadgetsFix {

    private final String replacementString;

    public IntLiteralMayBeLongLiteralFix(String replacementString) {
      this.replacementString = replacementString;
    }

    @Nonnull
    public LocalizeValue getName() {
      return InspectionGadgetsLocalize.intLiteralMayBeLongLiteralQuickfix(replacementString);
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiTypeCastExpression)) {
        return;
      }
      final PsiTypeCastExpression typeCastExpression =
        (PsiTypeCastExpression)element;
      replaceExpression(typeCastExpression, replacementString);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IntLiteralMayBeLongLiteralVisitor();
  }

  private static class IntLiteralMayBeLongLiteralVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (!PsiType.INT.equals(type)) {
        return;
      }
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiPrefixExpression ||
             parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiTypeCastExpression)) {
        return;
      }
      final PsiTypeCastExpression typeCastExpression =
        (PsiTypeCastExpression)parent;
      final PsiType castType = typeCastExpression.getType();
      if (!PsiType.LONG.equals(castType)) {
        return;
      }
      registerError(typeCastExpression, typeCastExpression);
    }
  }
}
