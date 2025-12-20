/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.trivialif;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ConditionalUtils;
import com.intellij.java.impl.ipp.psiutils.EquivalenceChecker;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;

class SimplifyIfElsePredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    PsiJavaToken token = (PsiJavaToken)element;
    PsiElement parent = token.getParent();
    if (!(parent instanceof PsiIfStatement)) {
      return false;
    }
    PsiIfStatement ifStatement = (PsiIfStatement)parent;
    if (ErrorUtil.containsError(ifStatement)) {
      return false;
    }
    PsiExpression condition = ifStatement.getCondition();
    if (condition == null) {
      return false;
    }
    if (isSimplifiableAssignment(ifStatement)) {
      return true;
    }
    if (isSimplifiableReturn(ifStatement)) {
      return true;
    }
    if (isSimplifiableImplicitReturn(ifStatement)) {
      return true;
    }
    if (isSimplifiableAssignmentNegated(ifStatement)) {
      return true;
    }
    if (isSimplifiableReturnNegated(ifStatement)) {
      return true;
    }
    if (isSimplifiableImplicitReturnNegated(ifStatement)) {
      return true;
    }
    if (isSimplifiableImplicitAssignment(ifStatement)) {
      return true;
    }
    return isSimplifiableImplicitAssignmentNegated(ifStatement);
  }

  public static boolean isSimplifiableImplicitReturn(
    PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiElement nextStatement =
      PsiTreeUtil.skipSiblingsForward(ifStatement,
                                      PsiWhiteSpace.class);
    if (!(nextStatement instanceof PsiStatement)) {
      return false;
    }
    PsiStatement elseBranch = (PsiStatement)nextStatement;
    return ConditionalUtils.isReturn(thenBranch, "true")
           && ConditionalUtils.isReturn(elseBranch, "false");
  }

  public static boolean isSimplifiableImplicitReturnNegated(
    PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiElement nextStatement =
      PsiTreeUtil.skipSiblingsForward(ifStatement,
                                      PsiWhiteSpace.class);
    if (!(nextStatement instanceof PsiStatement)) {
      return false;
    }
    PsiStatement elseBranch = (PsiStatement)nextStatement;
    return ConditionalUtils.isReturn(thenBranch, "false")
           && ConditionalUtils.isReturn(elseBranch, "true");
  }

  public static boolean isSimplifiableReturn(PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    return ConditionalUtils.isReturn(thenBranch, "true")
           && ConditionalUtils.isReturn(elseBranch, "false");
  }

  public static boolean isSimplifiableReturnNegated(
    PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    return ConditionalUtils.isReturn(thenBranch, "false")
           && ConditionalUtils.isReturn(elseBranch, "true");
  }

  public static boolean isSimplifiableAssignment(PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    if (!(ConditionalUtils.isAssignment(thenBranch, "true") &&
          ConditionalUtils.isAssignment(elseBranch, "false"))) {
      return false;
    }
    PsiExpressionStatement thenExpressionStatement =
      (PsiExpressionStatement)thenBranch;
    PsiAssignmentExpression thenExpression =
      (PsiAssignmentExpression)
        thenExpressionStatement.getExpression();
    PsiExpressionStatement elseExpressionStatement =
      (PsiExpressionStatement)elseBranch;
    PsiAssignmentExpression elseExpression =
      (PsiAssignmentExpression)
        elseExpressionStatement.getExpression();
    IElementType thenTokenType = thenExpression.getOperationTokenType();
    if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
      return false;
    }
    PsiExpression thenLhs = thenExpression.getLExpression();
    PsiExpression elseLhs = elseExpression.getLExpression();
    return EquivalenceChecker.expressionsAreEquivalent(thenLhs, elseLhs);
  }

  public static boolean isSimplifiableAssignmentNegated(
    PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    if (!ConditionalUtils.isAssignment(thenBranch, "false") ||
        !ConditionalUtils.isAssignment(elseBranch, "true")) {
      return false;
    }
    PsiExpressionStatement thenExpressionStatement =
      (PsiExpressionStatement)thenBranch;
    PsiAssignmentExpression thenExpression =
      (PsiAssignmentExpression)
        thenExpressionStatement.getExpression();
    PsiExpressionStatement elseExpressionStatement =
      (PsiExpressionStatement)elseBranch;
    PsiAssignmentExpression elseExpression =
      (PsiAssignmentExpression)
        elseExpressionStatement.getExpression();
    IElementType thenTokenType = thenExpression.getOperationTokenType();
    if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
      return false;
    }
    PsiExpression thenLhs = thenExpression.getLExpression();
    PsiExpression elseLhs = elseExpression.getLExpression();
    return EquivalenceChecker.expressionsAreEquivalent(thenLhs, elseLhs);
  }

  public static boolean isSimplifiableImplicitAssignment(
    PsiIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    PsiElement previousStatement =
      PsiTreeUtil.skipSiblingsBackward(ifStatement,
                                       PsiWhiteSpace.class);
    if (!(previousStatement instanceof PsiStatement)) {
      return false;
    }
    PsiStatement elseBranch = (PsiStatement)previousStatement;
    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    if (!ConditionalUtils.isAssignment(thenBranch, "true") ||
        !ConditionalUtils.isAssignment(elseBranch, "false")) {
      return false;
    }
    PsiAssignmentExpression thenExpression =
      (PsiAssignmentExpression)
        ((PsiExpressionStatement)thenBranch).getExpression();
    PsiAssignmentExpression elseExpression =
      (PsiAssignmentExpression)
        ((PsiExpressionStatement)elseBranch).getExpression();
    IElementType thenTokenType = thenExpression.getOperationTokenType();
    if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
      return false;
    }
    PsiExpression thenLhs = thenExpression.getLExpression();
    PsiExpression elseLhs = elseExpression.getLExpression();
    return EquivalenceChecker.expressionsAreEquivalent(thenLhs, elseLhs);
  }

  public static boolean isSimplifiableImplicitAssignmentNegated(
    PsiIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    PsiElement previousStatement =
      PsiTreeUtil.skipSiblingsBackward(ifStatement,
                                       PsiWhiteSpace.class);
    if (!(previousStatement instanceof PsiStatement)) {
      return false;
    }
    PsiStatement elseBranch = (PsiStatement)previousStatement;
    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    if (!ConditionalUtils.isAssignment(thenBranch, "false") ||
        !ConditionalUtils.isAssignment(elseBranch, "true")) {
      return false;
    }
    PsiExpressionStatement thenExpressionStatement =
      (PsiExpressionStatement)thenBranch;
    PsiAssignmentExpression thenExpression =
      (PsiAssignmentExpression)
        thenExpressionStatement.getExpression();
    PsiExpressionStatement elseExpressionStatement =
      (PsiExpressionStatement)elseBranch;
    PsiAssignmentExpression elseExpression =
      (PsiAssignmentExpression)
        elseExpressionStatement.getExpression();
    IElementType thenTokenType = thenExpression.getOperationTokenType();
    if (!thenTokenType.equals(elseExpression.getOperationTokenType())) {
      return false;
    }
    PsiExpression thenLhs = thenExpression.getLExpression();
    PsiExpression elseLhs = elseExpression.getLExpression();
    return EquivalenceChecker.expressionsAreEquivalent(thenLhs, elseLhs);
  }
}