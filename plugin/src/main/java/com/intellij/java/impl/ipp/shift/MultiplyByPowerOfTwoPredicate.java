/*
 * Copyright 2003-2005 Dave Griffith
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
package com.intellij.java.impl.ipp.shift;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class MultiplyByPowerOfTwoPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (element instanceof PsiBinaryExpression) {
      final PsiBinaryExpression expression = (PsiBinaryExpression)element;
      return binaryExpressionIsMultiplyByPowerOfTwo(expression);
    }
    else if (element instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression expression =
        (PsiAssignmentExpression)element;
      return assignmentExpressionIsMultiplyByPowerOfTwo(expression);
    }
    else {
      return false;
    }
  }

  private static boolean assignmentExpressionIsMultiplyByPowerOfTwo(
    PsiAssignmentExpression expression) {
    final IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.ASTERISKEQ) &&
        !tokenType.equals(JavaTokenType.DIVEQ)) {
      return false;
    }
    final PsiExpression lhs = expression.getLExpression();
    final PsiType lhsType = lhs.getType();
    if (lhsType == null) {
      return false;
    }
    if (!ShiftUtils.isIntegral(lhsType)) {
      return false;
    }
    final PsiExpression rhs = expression.getRExpression();
    if (rhs == null) {
      return false;
    }
    return ShiftUtils.isPowerOfTwo(rhs);
  }

  private static boolean binaryExpressionIsMultiplyByPowerOfTwo(
    PsiBinaryExpression expression) {
    final IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.ASTERISK) &&
        !tokenType.equals(JavaTokenType.DIV)) {
      return false;
    }
    final PsiExpression lhs = expression.getLOperand();
    final PsiType lhsType = lhs.getType();
    if (lhsType == null) {
      return false;
    }
    if (!ShiftUtils.isIntegral(lhsType)) {
      return false;
    }
    final PsiExpression rhs = expression.getROperand();
    if (rhs == null) {
      return false;
    }
    return ShiftUtils.isPowerOfTwo(rhs);
  }
}