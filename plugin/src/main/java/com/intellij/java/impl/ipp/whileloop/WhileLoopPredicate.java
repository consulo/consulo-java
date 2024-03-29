/*
 * Copyright 2006-2007 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.whileloop;

import com.intellij.java.language.psi.JavaTokenType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiWhileStatement;
import consulo.language.ast.IElementType;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class WhileLoopPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final PsiJavaToken token = (PsiJavaToken)element;
    final IElementType tokenType = token.getTokenType();
    if (!JavaTokenType.WHILE_KEYWORD.equals(tokenType)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiWhileStatement)) {
      return false;
    }
    final PsiWhileStatement whileStatement = (PsiWhileStatement)parent;
    return !(whileStatement.getCondition() == null ||
             whileStatement.getBody() == null);
  }
}