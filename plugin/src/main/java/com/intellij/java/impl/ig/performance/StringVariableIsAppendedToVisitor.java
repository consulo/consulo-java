/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import consulo.language.ast.IElementType;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;

class StringVariableIsAppendedToVisitor extends JavaRecursiveElementVisitor {

  private boolean appendedTo = false;
  private final PsiVariable variable;
  private final boolean onlyWarnOnLoop;

  StringVariableIsAppendedToVisitor(PsiVariable variable,
                                    boolean onlyWarnOnLoop) {
    super();
    this.variable = variable;
    this.onlyWarnOnLoop = onlyWarnOnLoop;
  }

  @Override
  public void visitAssignmentExpression(
    @Nonnull PsiAssignmentExpression assignment) {
    if (appendedTo) {
      return;
    }
    super.visitAssignmentExpression(assignment);
    PsiExpression lhs = assignment.getLExpression();
    PsiExpression rhs = assignment.getRExpression();
    if (rhs == null) {
      return;
    }
    if (!(lhs instanceof PsiReferenceExpression)) {
      return;
    }
    PsiReferenceExpression reference = (PsiReferenceExpression)lhs;
    PsiExpression qualifier = reference.getQualifierExpression();
    if (qualifier != null) {
      return;
    }
    PsiElement referent = reference.resolve();
    if (!variable.equals(referent)) {
      return;
    }
    IElementType tokenType = assignment.getOperationTokenType();
    if (tokenType.equals(JavaTokenType.PLUSEQ)) {
      if (onlyWarnOnLoop && !ControlFlowUtils.isInLoop(assignment)) {
        return;
      }
      appendedTo = true;
    }
    else if (isConcatenation(rhs)) {
      if (onlyWarnOnLoop && !ControlFlowUtils.isInLoop(assignment)) {
        return;
      }
      appendedTo = true;
    }
  }

  private boolean isConcatenation(PsiExpression expression) {
    if (expression == null) {
      return false;
    }
    if (expression instanceof PsiReferenceExpression) {
      PsiElement referent = ((PsiReference)expression).resolve();
      return variable.equals(referent);
    }
    if (expression instanceof PsiParenthesizedExpression) {
      PsiExpression body =
        ((PsiParenthesizedExpression)expression).getExpression();
      return isConcatenation(body);
    }
    if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binaryExpression =
        (PsiBinaryExpression)expression;
      PsiExpression lhs = binaryExpression.getLOperand();
      PsiExpression rhs = binaryExpression.getROperand();
      return isConcatenation(lhs) || isConcatenation(rhs);
    }
    return false;
  }

  public boolean isAppendedTo() {
    return appendedTo;
  }
}
