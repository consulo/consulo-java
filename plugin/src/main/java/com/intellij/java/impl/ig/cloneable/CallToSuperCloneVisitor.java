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
package com.intellij.java.impl.ig.cloneable;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import jakarta.annotation.Nonnull;

class CallToSuperCloneVisitor extends JavaRecursiveElementVisitor {

  private boolean callToSuperCloneFound = false;

  @Override
  public void visitElement(@Nonnull PsiElement element) {
    if (callToSuperCloneFound) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitMethodCallExpression(
    @Nonnull PsiMethodCallExpression expression) {
    if (callToSuperCloneFound) {
      return;
    }
    super.visitMethodCallExpression(expression);
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();
    final PsiExpression target = methodExpression.getQualifierExpression();
    if (!(target instanceof PsiSuperExpression)) {
      return;
    }
    final String methodName = methodExpression.getReferenceName();
    if (!HardcodedMethodConstants.CLONE.equals(methodName)) {
      return;
    }
    callToSuperCloneFound = true;
  }

  public boolean isCallToSuperCloneFound() {
    return callToSuperCloneFound;
  }
}
