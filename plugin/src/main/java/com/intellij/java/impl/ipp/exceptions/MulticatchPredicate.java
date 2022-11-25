/*
 * Copyright 2011-2013 Bas Leijdekkers
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

import com.intellij.java.language.psi.PsiCatchSection;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiDisjunctionType;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.*;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;

class MulticatchPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (element instanceof PsiCodeBlock) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiCatchSection)) {
      return false;
    }
    final PsiCatchSection catchSection = (PsiCatchSection)parent;
    final PsiType type = catchSection.getCatchType();
    return type instanceof PsiDisjunctionType;
  }
}
