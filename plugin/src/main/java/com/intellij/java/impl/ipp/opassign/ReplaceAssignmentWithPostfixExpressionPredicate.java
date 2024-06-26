/*
 * Copyright 2009-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.opassign;

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiBinaryExpression;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiLiteral;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.ast.IElementType;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class ReplaceAssignmentWithPostfixExpressionPredicate implements PsiElementPredicate {

  private static final Integer ONE = Integer.valueOf(1);

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiAssignmentExpression)) {
      return false;
    }
    final PsiAssignmentExpression assignmentExpression =
      (PsiAssignmentExpression)element;
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final PsiExpression strippedLhs =
      ParenthesesUtils.stripParentheses(lhs);
    if (!(strippedLhs instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)strippedLhs;
    final PsiElement target = referenceExpression.resolve();
    if (!(target instanceof PsiVariable)) {
      return false;
    }
    final PsiVariable variable = (PsiVariable)target;
    final PsiExpression rhs = assignmentExpression.getRExpression();
    if (!(rhs instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)rhs;
    final PsiExpression rOperand = binaryExpression.getROperand();
    if (rOperand == null) {
      return false;
    }
    final PsiExpression lOperand = binaryExpression.getLOperand();
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (lOperand instanceof PsiLiteral) {
      final PsiLiteral literal = (PsiLiteral)lOperand;
      final Object value = literal.getValue();
      if (ONE != value) {
        return false;
      }
      if (!VariableAccessUtils.evaluatesToVariable(rOperand, variable)) {
        return false;
      }
      return JavaTokenType.PLUS.equals(tokenType);
    }
    else if (rOperand instanceof PsiLiteral) {
      final PsiLiteral literal = (PsiLiteral)rOperand;
      final Object value = literal.getValue();
      if (ONE != value) {
        return false;
      }
      if (!VariableAccessUtils.evaluatesToVariable(lOperand, variable)) {
        return false;
      }
      return !(!JavaTokenType.PLUS.equals(tokenType) &&
               !JavaTokenType.MINUS.equals(tokenType));
    }
    return false;
  }
}