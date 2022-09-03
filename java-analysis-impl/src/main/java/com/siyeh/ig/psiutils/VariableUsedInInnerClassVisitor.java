/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import javax.annotation.Nonnull;
import com.intellij.java.language.psi.JavaRecursiveElementVisitor;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.PsiVariable;

class VariableUsedInInnerClassVisitor extends JavaRecursiveElementVisitor {

  @Nonnull
  private final PsiVariable variable;
  private boolean usedInInnerClass = false;
  private boolean inInnerClass = false;

  public VariableUsedInInnerClassVisitor(@Nonnull PsiVariable variable) {
    super();
    this.variable = variable;
  }

  @Override
  public void visitElement(@Nonnull PsiElement element) {
    if (!usedInInnerClass) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitClass(@Nonnull PsiClass psiClass) {
    if (usedInInnerClass) {
      return;
    }
    final boolean wasInInnerClass = inInnerClass;
    inInnerClass = true;
    super.visitClass(psiClass);
    inInnerClass = wasInInnerClass;
  }

  @Override
  public void visitReferenceExpression(
    @Nonnull PsiReferenceExpression referenceExpression) {
    if (usedInInnerClass) {
      return;
    }
    super.visitReferenceExpression(referenceExpression);
    if (!inInnerClass) {
      return;
    }
    final PsiElement target = referenceExpression.resolve();
    if (variable.equals(target)) {
      usedInInnerClass = true;
    }
  }

  public boolean isUsedInInnerClass() {
    return usedInInnerClass;
  }
}