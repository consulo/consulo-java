/*
 * Copyright 2007-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.expression;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class ExpressionPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiPolyadicExpression)) {
      return false;
    }
    final PsiPolyadicExpression expression = (PsiPolyadicExpression)parent;
    final PsiExpression[] operands = expression.getOperands();
    if (operands.length < 2) {
      return false;
    }
    PsiExpression prevOperand = null;
    for (PsiExpression operand : operands) {
      final PsiJavaToken token = expression.getTokenBeforeOperand(operand);
      if (element == token) {
        if (prevOperand == null || operand.getText().equals(prevOperand.getText())) {
          return false;
        }
        break;
      }
      prevOperand = operand;
    }
    return !ComparisonUtils.isComparison(expression);
  }
}