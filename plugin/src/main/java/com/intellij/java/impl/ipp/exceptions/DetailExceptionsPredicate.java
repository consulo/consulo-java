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
package com.intellij.java.impl.ipp.exceptions;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;

import java.util.HashSet;
import java.util.Set;

class DetailExceptionsPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    final IElementType tokenType = ((PsiJavaToken)element).getTokenType();
    if (!JavaTokenType.TRY_KEYWORD.equals(tokenType) && !JavaTokenType.CATCH_KEYWORD.equals(tokenType)) {
      return false;
    }
    PsiElement parent = element.getParent();
    if (parent instanceof PsiCatchSection) {
      parent = parent.getParent();
    }
    if (!(parent instanceof PsiTryStatement)) {
      return false;
    }
    final PsiTryStatement tryStatement = (PsiTryStatement)parent;
    if (ErrorUtil.containsError(tryStatement)) {
      return false;
    }
    final Set<PsiType> exceptionsThrown = new HashSet<PsiType>(10);
    final PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    final PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      ExceptionUtils.calculateExceptionsThrownForResourceList(resourceList, exceptionsThrown);
    }
    ExceptionUtils.calculateExceptionsThrownForCodeBlock(tryBlock, exceptionsThrown);
    final Set<PsiType> exceptionsCaught = ExceptionUtils.getExceptionTypesHandled(tryStatement);
    for (PsiType typeThrown : exceptionsThrown) {
      if (exceptionsCaught.contains(typeThrown)) {
        continue;
      }
      for (PsiType typeCaught : exceptionsCaught) {
        if (typeCaught.isAssignableFrom(typeThrown)) {
          return true;
        }
      }
    }
    return false;
  }
}
