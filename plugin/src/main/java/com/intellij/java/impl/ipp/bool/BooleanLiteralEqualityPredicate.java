/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.bool;

import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.siyeh.ig.psiutils.BoolUtils;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;

class BooleanLiteralEqualityPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiBinaryExpression)) {
      return false;
    }
    final PsiBinaryExpression expression = (PsiBinaryExpression)element;
    final IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.EQEQ) &&
        !tokenType.equals(JavaTokenType.NE)) {
      return false;
    }
    final PsiExpression lhs = expression.getLOperand();
    final PsiExpression rhs = expression.getROperand();
    if (rhs == null) {
      return false;
    }
    if (!BoolUtils.isBooleanLiteral(lhs) &&
        !BoolUtils.isBooleanLiteral(rhs)) {
      return false;
    }
    final PsiType type = expression.getType();
    if (!PsiType.BOOLEAN.equals(type)) {
      return false;
    }
    return !ErrorUtil.containsError(element);
  }
}