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
package com.intellij.java.impl.ipp.equality;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;

class ObjectEqualityPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiBinaryExpression)) {
      return false;
    }
    PsiBinaryExpression expression = (PsiBinaryExpression)element;
    IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.NE) &&
        !tokenType.equals(JavaTokenType.EQEQ)) {
      return false;
    }
    PsiExpression lhs = expression.getLOperand();
    String lhsText = lhs.getText();
    if (PsiKeyword.NULL.equals(lhsText)) {
      return false;
    }
    PsiType lhsType = lhs.getType();
    if (lhsType == null) {
      return false;
    }
    PsiExpression rhs = expression.getROperand();
    if (rhs == null) {
      return false;
    }
    String rhsText = rhs.getText();
    if (PsiKeyword.NULL.equals(rhsText)) {
      return false;
    }
    PsiType rhsType = rhs.getType();
    if (rhsType == null) {
      return false;
    }
    if (TypeConversionUtil.isPrimitiveAndNotNull(lhsType) ||
        TypeConversionUtil.isPrimitiveAndNotNull(rhsType)) {
      return false;
    }
    if (rhsType instanceof PsiClassType) {
      PsiClassType rhsClassType = (PsiClassType)rhsType;
      PsiClass rhsClass = rhsClassType.resolve();
      if (rhsClass != null && rhsClass.isEnum()) {
        return false;
      }
    }
    if (lhsType instanceof PsiClassType) {
      PsiClassType lhsClassType = (PsiClassType)lhsType;
      PsiClass lhsClass = lhsClassType.resolve();
      if (lhsClass != null && lhsClass.isEnum()) {
        return false;
      }
    }
    return !ErrorUtil.containsError(element);
  }
}