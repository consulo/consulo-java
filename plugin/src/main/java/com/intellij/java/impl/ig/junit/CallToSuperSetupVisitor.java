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
package com.intellij.java.impl.ig.junit;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

class CallToSuperSetupVisitor extends JavaRecursiveElementVisitor {

  private boolean callToSuperSetupFound = false;

  @Override
  public void visitElement(@Nonnull PsiElement element) {
    if (!callToSuperSetupFound) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitMethodCallExpression(
    @Nonnull PsiMethodCallExpression expression) {
    if (callToSuperSetupFound) {
      return;
    }
    super.visitMethodCallExpression(expression);
    final PsiReferenceExpression methodExpression =
      expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"setUp".equals(methodName)) {
      return;
    }
    final PsiExpression target = methodExpression.getQualifierExpression();
    if (!(target instanceof PsiSuperExpression)) {
      return;
    }

    callToSuperSetupFound = true;
  }

  public boolean isCallToSuperSetupFound() {
    return callToSuperSetupFound;
  }
}