/*
 * Copyright 2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

class ThisPassedAsArgumentVisitor extends JavaRecursiveElementVisitor {

  private boolean passed = false;

  public ThisPassedAsArgumentVisitor() {}

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
      if (isThisExpression(argument)) {
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
      if (isThisExpression(argument)) {
        passed = true;
        break;
      }
    }
  }

  private static boolean isThisExpression(PsiExpression expression) {
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      return isThisExpression(parenthesizedExpression.getExpression());
    }
    return expression instanceof PsiThisExpression;
  }

  public boolean isPassed() {
    return passed;
  }
}