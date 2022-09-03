/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiVariable;

class VariablePassedAsArgumentVisitor extends JavaRecursiveElementVisitor {

  @Nonnull
  private final PsiVariable variable;
  private boolean passed = false;

  public VariablePassedAsArgumentVisitor(@Nonnull PsiVariable variable) {
    super();
    this.variable = variable;
  }

  @Override
  public void visitElement(@Nonnull PsiElement element) {
    if (!passed) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression call) {
    if (passed) {
      return;
    }
    super.visitMethodCallExpression(call);
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    for (PsiExpression argument : arguments) {
      if (VariableAccessUtils.mayEvaluateToVariable(argument, variable)) {
        passed = true;
        break;
      }
    }
  }

  @Override
  public void visitNewExpression(@Nonnull PsiNewExpression newExpression) {
    if (passed) {
      return;
    }
    super.visitNewExpression(newExpression);
    final PsiExpressionList argumentList = newExpression.getArgumentList();
    if (argumentList == null) {
      return;
    }
    final PsiExpression[] arguments = argumentList.getExpressions();
    for (PsiExpression argument : arguments) {
      if (VariableAccessUtils.mayEvaluateToVariable(argument, variable)) {
        passed = true;
        break;
      }
    }
  }

  public boolean isPassed() {
    return passed;
  }
}