/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.impl.ipp.exceptions;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

/**
 * @author Bas Leijdekkers
 */
class TryWithMultipleResourcesPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {

    if (element instanceof PsiJavaToken) {
      final PsiJavaToken javaToken = (PsiJavaToken)element;
      final IElementType tokenType = javaToken.getTokenType();
      if (!JavaTokenType.TRY_KEYWORD.equals(tokenType)) {
        return false;
      }
    }
    else if (!(element instanceof PsiResourceList)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiTryStatement)) {
      return false;
    }
    final PsiTryStatement tryStatement = (PsiTryStatement)parent;
    final PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      return false;
    }
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList == null) {
      return false;
    }
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) {
      return false;
    }
    return resourceList.getResourceVariables().size() > 1;
  }
}
