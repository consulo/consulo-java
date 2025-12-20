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
package com.intellij.java.impl.ig.finalization;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.ExpressionUtils;
import jakarta.annotation.Nonnull;

class CallToSuperFinalizeVisitor extends JavaRecursiveElementVisitor {

  private boolean callToSuperFinalizeFound = false;

  @Override
  public void visitElement(@Nonnull PsiElement element) {
    if (!callToSuperFinalizeFound) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitIfStatement(PsiIfStatement statement) {
    PsiExpression condition = statement.getCondition();
    Object result =
      ExpressionUtils.computeConstantExpression(condition);
    if (result != null && result.equals(Boolean.FALSE)) {
      return;
    }
    super.visitIfStatement(statement);
  }

  @Override
  public void visitMethodCallExpression(
    @Nonnull PsiMethodCallExpression expression) {
    if (callToSuperFinalizeFound) {
      return;
    }
    super.visitMethodCallExpression(expression);
    PsiReferenceExpression methodExpression =
      expression.getMethodExpression();
    PsiExpression target = methodExpression.getQualifierExpression();
    if (!(target instanceof PsiSuperExpression)) {
      return;
    }
    String methodName = methodExpression.getReferenceName();
    if (!HardcodedMethodConstants.FINALIZE.equals(methodName)) {
      return;
    }
    callToSuperFinalizeFound = true;
  }

  public boolean isCallToSuperFinalizeFound() {
    return callToSuperFinalizeFound;
  }
}