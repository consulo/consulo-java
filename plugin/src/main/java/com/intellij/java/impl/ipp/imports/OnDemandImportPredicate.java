/*
 * Copyright 2006 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.imports;

import com.intellij.java.language.psi.PsiImportStatement;
import com.intellij.java.language.psi.PsiImportStatementBase;
import com.intellij.java.language.psi.PsiJavaFile;
import consulo.language.psi.*;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import jakarta.annotation.Nonnull;

class OnDemandImportPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(@Nonnull PsiElement element) {
    // doesn't work for import static yet.
    if (!(element instanceof PsiImportStatement)) {
      return false;
    }
    final PsiImportStatementBase importStatementBase =
      (PsiImportStatementBase)element;
    if (!importStatementBase.isOnDemand()) {
      return false;
    }
    final PsiFile file = importStatementBase.getContainingFile();
    return file instanceof PsiJavaFile;
  }
}