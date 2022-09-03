/*
 * Copyright 2011-2012 Bas Leijdekkers
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

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiReferenceList;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class ObscureThrownExceptionsPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiReferenceList)) {
      return false;
    }
    final PsiReferenceList throwsList = (PsiReferenceList)element;
    if (throwsList.getReferenceElements().length < 2) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiMethod)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)parent;
    return method.getThrowsList().equals(element);
  }
}
