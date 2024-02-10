/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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

import java.util.Set;

import jakarta.annotation.Nonnull;
import com.intellij.java.language.psi.JavaRecursiveElementVisitor;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiNewExpression;
import com.intellij.java.language.psi.PsiVariable;

class VariablePassedAsArgumentExcludedVisitor extends JavaRecursiveElementVisitor {

  @Nonnull
  private final PsiVariable variable;
  private final Set<String> excludes;
  private final boolean myBuilderPattern;

  private boolean passed = false;

  public VariablePassedAsArgumentExcludedVisitor(@Nonnull PsiVariable variable, @Nonnull Set<String> excludes, boolean builderPattern) {
    this.variable = variable;
    this.excludes = excludes;
    myBuilderPattern = builderPattern;
  }

  @Override
  public void visitElement(@Nonnull PsiElement element) {
    if (passed) {
      return;
    }
    super.visitElement(element);
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
      if (!VariableAccessUtils.mayEvaluateToVariable(argument, variable, myBuilderPattern)) {
        continue;
      }
      final PsiMethod method = call.resolveMethod();
      if (method != null) {
        final PsiClass aClass = method.getContainingClass();
        if (aClass != null) {
          final String name = aClass.getQualifiedName();
          if (excludes.contains(name)) {
            continue;
          }
        }
      }
      passed = true;
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
      if (!VariableAccessUtils.mayEvaluateToVariable(argument, variable, myBuilderPattern)) {
        continue;
      }
      final PsiMethod constructor = newExpression.resolveConstructor();
      if (constructor != null) {
        final PsiClass aClass = constructor.getContainingClass();
        if (aClass != null) {
          final String name = aClass.getQualifiedName();
          if (excludes.contains(name)) {
            continue;
          }
        }
      }
      passed = true;
    }
  }

  public boolean isPassed() {
    return passed;
  }
}