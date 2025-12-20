/*
 * Copyright 2006-2012 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.parenthesis;

import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

class AddClarifyingParenthesesPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(@Nonnull PsiElement element) {
    PsiElement parent = element.getParent();
    if (mightBeConfusingExpression(parent)) {
      return false;
    }
    if (element instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)element;
      IElementType tokenType = polyadicExpression.getOperationTokenType();
      PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (operand instanceof PsiInstanceOfExpression) {
          return true;
        }
        if (!(operand instanceof PsiPolyadicExpression)) {
          continue;
        }
        PsiPolyadicExpression expression = (PsiPolyadicExpression)operand;
        IElementType otherTokenType = expression.getOperationTokenType();
        if (!tokenType.equals(otherTokenType)) {
          return true;
        }
      }
    }
    else if (element instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element;
      PsiExpression condition = conditionalExpression.getCondition();
      if (mightBeConfusingExpression(condition)) {
        return true;
      }
      PsiExpression thenExpression = conditionalExpression.getThenExpression();
      if (mightBeConfusingExpression(thenExpression)) {
        return true;
      }
      PsiExpression elseExpression = conditionalExpression.getElseExpression();
      if (mightBeConfusingExpression(elseExpression)) {
        return true;
      }
    }
    else if (element instanceof PsiInstanceOfExpression) {
      PsiInstanceOfExpression instanceOfExpression = (PsiInstanceOfExpression)element;
      PsiExpression operand = instanceOfExpression.getOperand();
      if (mightBeConfusingExpression(operand)) {
        return true;
      }
    }
    else if (element instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)element;
      PsiExpression rhs = assignmentExpression.getRExpression();
      if (!(mightBeConfusingExpression(rhs))) {
        return false;
      }
      if (rhs instanceof PsiAssignmentExpression) {
        PsiAssignmentExpression nestedAssignment = (PsiAssignmentExpression)rhs;
        IElementType nestedTokenType = nestedAssignment.getOperationTokenType();
        IElementType tokenType = assignmentExpression.getOperationTokenType();
        if (nestedTokenType.equals(tokenType)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static boolean mightBeConfusingExpression(@Nullable PsiElement element) {
    return element instanceof PsiPolyadicExpression || element instanceof PsiConditionalExpression ||
           element instanceof PsiInstanceOfExpression || element instanceof PsiAssignmentExpression;
  }
}