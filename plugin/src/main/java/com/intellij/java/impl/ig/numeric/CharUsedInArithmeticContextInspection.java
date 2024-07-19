/*
 * Copyright 2008-2011 Bas Leijdekkers
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
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class CharUsedInArithmeticContextInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.charUsedInArithmeticContextDisplayName().get();
  }

  @Override
  @Nonnull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.charUsedInArithmeticContextProblemDescriptor().get();
  }

  @Nonnull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> result = new ArrayList<InspectionGadgetsFix>();
    final PsiElement expression = (PsiElement)infos[0];
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpression) {
      final PsiExpression binaryExpression = (PsiExpression)parent;
      final PsiType type = binaryExpression.getType();
      if (type instanceof PsiPrimitiveType && !type.equals(PsiType.CHAR)) {
        final String typeText = type.getCanonicalText();
        result.add(new CharUsedInArithmeticContentCastFix(typeText));
      }
    }
    if (!(expression instanceof PsiLiteralExpression)) {
      return result.toArray(new InspectionGadgetsFix[result.size()]);
    }
    while (parent instanceof PsiPolyadicExpression) {
      if (ExpressionUtils.hasStringType((PsiExpression)parent)) {
        result.add(new CharUsedInArithmeticContentFix());
        break;
      }
      parent = parent.getParent();
    }

    return result.toArray(new InspectionGadgetsFix[result.size()]);
  }

  private static class CharUsedInArithmeticContentFix extends InspectionGadgetsFix {

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.charUsedInArithmeticContextQuickfix().get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiLiteralExpression)) {
        return;
      }
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      final Object literal = literalExpression.getValue();
      if (!(literal instanceof Character)) {
        return;
      }
      final String escaped = StringUtil.escapeStringCharacters(literal.toString());
      replaceExpression(literalExpression, '\"' + escaped + '"');
    }
  }

  private static class CharUsedInArithmeticContentCastFix extends InspectionGadgetsFix {

    private final String typeText;

    CharUsedInArithmeticContentCastFix(String typeText) {
      this.typeText = typeText;
    }

    @Nonnull
    public String getName() {
      return InspectionGadgetsLocalize.charUsedInArithmeticContextCastQuickfix(typeText).get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiExpression)) {
        return;
      }
      final PsiExpression expression = (PsiExpression)element;
      final String expressionText = expression.getText();
      replaceExpression(expression, '(' + typeText + ')' + expressionText);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CharUsedInArithmeticContextVisitor();
  }

  private static class CharUsedInArithmeticContextVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (ComparisonUtils.isComparisonOperation(tokenType)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      PsiType leftType = operands[0].getType();
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression operand = operands[i];
        final PsiType rightType = operand.getType();
        final PsiType expressionType = TypeConversionUtil.calcTypeForBinaryExpression(leftType, rightType, tokenType, true);
        if (TypeUtils.isJavaLangString(expressionType)) {
          return;
        }
        if (PsiType.CHAR.equals(rightType)) {
          registerError(operand, operand);
        }
        if (PsiType.CHAR.equals(leftType) && i == 1) {
          registerError(operands[0], operands[0]);
        }
        leftType = rightType;
      }
    }
  }
}
