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
package com.intellij.java.impl.ipp.constant;

import jakarta.annotation.Nullable;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class ConstantSubexpressionPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    final PsiJavaToken token;
    if (element instanceof PsiJavaToken) {
      token = (PsiJavaToken)element;
    }
    else {
      final PsiElement prevSibling = element.getPrevSibling();
      if (prevSibling instanceof PsiJavaToken) {
        token = (PsiJavaToken)prevSibling;
      }
      else {
        return false;
      }
    }

    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
    final PsiType type = polyadicExpression.getType();
    if (type == null || type.equalsToText("java.lang.String")) {
      // handled by JoinConcatenatedStringLiteralsIntention
      return false;
    }
    final PsiPolyadicExpression subexpression = getSubexpression(polyadicExpression, token);
    if (subexpression == null) {
      return false;
    }
    if (!isPartOfLargerExpression(polyadicExpression)) {
      // handled by ConstantExpressionIntention
      return false;
    }
    if (!PsiUtil.isConstantExpression(subexpression)) {
      return false;
    }
    final Object value = ExpressionUtils.computeConstantExpression(subexpression);
    return value != null;
  }

  @Nullable
  static PsiPolyadicExpression getSubexpression(PsiPolyadicExpression expression, PsiJavaToken token) {
    final PsiExpression[] operands = expression.getOperands();
    if (operands.length == 2) {
      return expression;
    }
    for (int i = 1; i < operands.length; i++) {
      final PsiExpression operand = operands[i];
      final PsiJavaToken currentToken = expression.getTokenBeforeOperand(operand);
      if (currentToken == token) {
        final String binaryExpressionText = operands[i - 1].getText() + ' ' + token.getText() + ' ' + operand.getText();
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
        return (PsiPolyadicExpression)factory.createExpressionFromText(binaryExpressionText, expression);
      }
    }
    return null;
  }

  private static boolean isPartOfLargerExpression(PsiPolyadicExpression expression) {
    if (expression.getOperands().length > 2) {
      return true;
    }
    final PsiElement containingElement = expression.getParent();
    if (containingElement instanceof PsiExpression) {
      final PsiExpression containingExpression =
        (PsiExpression)containingElement;
      if (!PsiUtil.isConstantExpression(containingExpression)) {
        return false;
      }
    }
    else {
      return false;
    }
    return true;
  }
}