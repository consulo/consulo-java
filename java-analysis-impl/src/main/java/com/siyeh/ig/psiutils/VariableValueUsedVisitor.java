/*
 * Copyright 2008-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.*;
import com.intellij.psi.*;
import consulo.language.ast.IElementType;

class VariableValueUsedVisitor extends JavaRecursiveElementVisitor {

  @Nonnull
  private final PsiVariable variable;
  private boolean read = false;
  private boolean written = false;

  VariableValueUsedVisitor(@Nonnull PsiVariable variable) {
    this.variable = variable;
  }

  @Override
  public void visitElement(@Nonnull PsiElement element) {
    if (read || written) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitAssignmentExpression(
    @Nonnull PsiAssignmentExpression assignment) {
    if (read || written) {
      return;
    }
    super.visitAssignmentExpression(assignment);
    final PsiExpression lhs = assignment.getLExpression();
    if (lhs instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)lhs;
      final PsiElement target = referenceExpression.resolve();
      if (variable.equals(target)) {
        written = true;
        return;
      }
    }
    final PsiExpression rhs = assignment.getRExpression();
    if (rhs == null) {
      return;
    }
    final VariableUsedVisitor visitor =
      new VariableUsedVisitor(variable);
    rhs.accept(visitor);
    read = visitor.isUsed();
  }

  @Override
  public void visitPrefixExpression(
    @Nonnull PsiPrefixExpression prefixExpression) {
    if (read || written) {
      return;
    }
    super.visitPrefixExpression(prefixExpression);
    final IElementType tokenType = prefixExpression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
        !tokenType.equals(JavaTokenType.MINUSMINUS)) {
      return;
    }
    final PsiExpression operand = prefixExpression.getOperand();
    if (!(operand instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)operand;
    final PsiElement target = referenceExpression.resolve();
    if (!variable.equals(target)) {
      return;
    }
    written = true;
  }

  @Override
  public void visitPostfixExpression(
    @Nonnull PsiPostfixExpression postfixExpression) {
    if (read || written) {
      return;
    }
    super.visitPostfixExpression(postfixExpression);
    final IElementType tokenType = postfixExpression.getOperationTokenType();
    if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
        !tokenType.equals(JavaTokenType.MINUSMINUS)) {
      return;
    }
    final PsiExpression operand = postfixExpression.getOperand();
    if (!(operand instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression referenceExpression =
      (PsiReferenceExpression)operand;
    final PsiElement target = referenceExpression.resolve();
    if (!variable.equals(target)) {
      return;
    }
    written = true;
  }

  @Override
  public void visitVariable(@Nonnull PsiVariable variable) {
    if (read || written) {
      return;
    }
    super.visitVariable(variable);
    final PsiExpression initalizer = variable.getInitializer();
    if (initalizer == null) {
      return;
    }
    final VariableUsedVisitor visitor =
      new VariableUsedVisitor(variable);
    initalizer.accept(visitor);
    read = visitor.isUsed();
  }

  @Override
  public void visitMethodCallExpression(
    @Nonnull PsiMethodCallExpression call) {
    if (read || written) {
      return;
    }
    super.visitMethodCallExpression(call);
    final PsiReferenceExpression methodExpression =
      call.getMethodExpression();
    final PsiExpression qualifier =
      methodExpression.getQualifierExpression();
    final VariableUsedVisitor visitor =
      new VariableUsedVisitor(variable);
    if (qualifier != null) {
      qualifier.accept(visitor);
      if (visitor.isUsed()) {
        read = true;
        return;
      }
    }
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    for (final PsiExpression argument : arguments) {
      argument.accept(visitor);
      if (visitor.isUsed()) {
        read = true;
        return;
      }
    }
  }

  @Override
  public void visitNewExpression(
    @Nonnull PsiNewExpression newExpression) {
    if (read || written) {
      return;
    }
    super.visitNewExpression(newExpression);
    final PsiExpressionList argumentList =
      newExpression.getArgumentList();
    if (argumentList == null) {
      return;
    }
    final PsiExpression[] arguments = argumentList.getExpressions();
    for (final PsiExpression argument : arguments) {
      final VariableUsedVisitor visitor =
        new VariableUsedVisitor(variable);
      argument.accept(visitor);
      if (visitor.isUsed()) {
        read = true;
        return;
      }
    }
  }

  @Override
  public void visitArrayInitializerExpression(
    PsiArrayInitializerExpression expression) {
    if (read || written) {
      return;
    }
    super.visitArrayInitializerExpression(expression);
    final PsiExpression[] arguments = expression.getInitializers();
    for (final PsiExpression argument : arguments) {
      final VariableUsedVisitor visitor =
        new VariableUsedVisitor(variable);
      argument.accept(visitor);
      if (visitor.isUsed()) {
        read = true;
        return;
      }
    }
  }

  @Override
  public void visitReturnStatement(
    @Nonnull PsiReturnStatement returnStatement) {
    if (read || written) {
      return;
    }
    super.visitReturnStatement(returnStatement);
    final PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      return;
    }
    final VariableUsedVisitor visitor =
      new VariableUsedVisitor(variable);
    returnValue.accept(visitor);
    read = visitor.isUsed();
  }

  /**
   * check if variable is used in nested/inner class.
   */
  @Override
  public void visitClass(PsiClass aClass) {
    if (read || written) {
      return;
    }
    super.visitClass(aClass);
    final VariableUsedVisitor visitor =
      new VariableUsedVisitor(variable);
    aClass.accept(visitor);
    read = visitor.isUsed();
  }

  public boolean isVariableValueUsed() {
    return read;
  }
}
