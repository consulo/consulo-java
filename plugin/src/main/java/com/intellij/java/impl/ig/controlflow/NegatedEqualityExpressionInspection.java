/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPrefixExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author Bas Leijdekkers
 */
@ExtensionImpl
public class NegatedEqualityExpressionInspection extends BaseInspection {

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.negatedEqualityExpressionDisplayName().get();
  }

  @Nonnull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsLocalize.negatedEqualityExpressionProblemDescriptor(infos[0]).get();
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new NegatedEqualityExpressionFix();
  }

  private static class NegatedEqualityExpressionFix extends InspectionGadgetsFix {

    @Nonnull
    @Override
    public String getName() {
      return InspectionGadgetsLocalize.negatedEqualityExpressionQuickfix().get();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiPrefixExpression)) {
        return;
      }
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parent;
      if (!JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
        return;
      }
      final PsiExpression operand = ParenthesesUtils.stripParentheses(prefixExpression.getOperand());
      if (!(operand instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)operand;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      StringBuilder text = new StringBuilder(binaryExpression.getLOperand().getText());
      if (JavaTokenType.EQEQ.equals(tokenType)) {
        text.append("!=");
      }
      else if (JavaTokenType.NE.equals(tokenType)) {
        text.append("==");
      }
      else {
        return;
      }
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs != null) {
        text.append(rhs.getText());
      }
      replaceExpression(prefixExpression, text.toString());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NegatedEqualsVisitor();
  }

  private static class NegatedEqualsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      if (!JavaTokenType.EXCL.equals(expression.getOperationTokenType())) {
        return;
      }
      final PsiExpression operand = ParenthesesUtils.stripParentheses(expression.getOperand());
      if (!(operand instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)operand;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (JavaTokenType.EQEQ.equals(tokenType)) {
        registerError(expression.getOperationSign(), "==");
      }
      else if (JavaTokenType.NE.equals(tokenType)) {
        registerError(expression.getOperationSign(), "!=");
      }
    }
  }
}
