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
package com.intellij.java.impl.ipp.decls;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;

class SimplifyVariablePredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiVariable)) {
      return false;
    }
    if (ErrorUtil.containsError(element)) {
      return false;
    }
    final PsiVariable var = (PsiVariable)element;
    final PsiTypeElement typeElement = var.getTypeElement();
    if (typeElement == null) {
      return false; // Could be true for enum constants.
    }

    final PsiType elementType = typeElement.getType();
    final PsiType type = var.getType();
    return elementType.getArrayDimensions() != type.getArrayDimensions();
  }
}