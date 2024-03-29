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
package com.intellij.java.impl.ig.performance;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;

class CanBeStaticVisitor extends JavaRecursiveElementVisitor {
  private boolean canBeStatic = true;

  @Override
  public void visitElement(@Nonnull PsiElement element) {
    if (canBeStatic) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitReferenceExpression(@Nonnull PsiReferenceExpression ref) {
    if (!canBeStatic) {
      return;
    }
    super.visitReferenceExpression(ref);
    final PsiElement element = ref.resolve();
    if (element instanceof PsiField) {
      final PsiField field = (PsiField)element;
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        canBeStatic = false;
      }
    }
    else if (element instanceof PsiVariable) {
      //can happen with initializers of inner classes referencing
      //local variables or parameters from outer class
      canBeStatic = false;
    }
  }

  public boolean canBeStatic() {
    return canBeStatic;
  }
}
