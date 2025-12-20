/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.forloop;

import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;

class IterableForEachLoopPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    PsiJavaToken token = (PsiJavaToken)element;
    IElementType tokenType = token.getTokenType();
    if (!JavaTokenType.FOR_KEYWORD.equals(tokenType)) {
      return false;
    }
    PsiElement parent = element.getParent();
    if (!(parent instanceof PsiForeachStatement)) {
      return false;
    }
    PsiForeachStatement foreachStatement = (PsiForeachStatement)parent;
    PsiExpression iteratedValue = foreachStatement.getIteratedValue();
    if (iteratedValue == null) {
      return false;
    }
    PsiType type = iteratedValue.getType();
    if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ITERABLE)) {
      return false;
    }
    return !ErrorUtil.containsError(foreachStatement);
  }
}