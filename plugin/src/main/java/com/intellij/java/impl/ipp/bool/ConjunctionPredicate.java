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
package com.intellij.java.impl.ipp.bool;

import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import consulo.language.ast.IElementType;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;

class ConjunctionPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiPolyadicExpression)) {
      return false;
    }
    PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
    IElementType tokenType = expression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.ANDAND) &&
        !tokenType.equals(JavaTokenType.OROR)) {
      return false;
    }
    return !ErrorUtil.containsError(element);
  }
}
