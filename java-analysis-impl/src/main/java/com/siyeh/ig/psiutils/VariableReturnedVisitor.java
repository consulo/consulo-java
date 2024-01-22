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

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.JavaRecursiveElementVisitor;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiReturnStatement;
import com.intellij.java.language.psi.PsiVariable;

class VariableReturnedVisitor extends JavaRecursiveElementVisitor {

  @Nonnull
  private final PsiVariable variable;
  private final boolean myBuilderPattern;

  private boolean returned = false;

  public VariableReturnedVisitor(@jakarta.annotation.Nonnull PsiVariable variable, boolean builderPattern) {
    this.variable = variable;
    myBuilderPattern = builderPattern;
  }

  @Override
  public void visitElement(PsiElement element) {
    if (returned) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitReturnStatement(@jakarta.annotation.Nonnull PsiReturnStatement returnStatement) {
    if (returned) {
      return;
    }
    super.visitReturnStatement(returnStatement);
    final PsiExpression returnValue = returnStatement.getReturnValue();
    if (VariableAccessUtils.mayEvaluateToVariable(returnValue, variable, myBuilderPattern)) {
      returned = true;
    }
  }

  public boolean isReturned() {
    return returned;
  }
}