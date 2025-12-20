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
package com.intellij.java.impl.ipp.trivialif;

import com.intellij.java.language.psi.*;
import consulo.language.psi.*;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ConditionalUtils;
import com.intellij.java.impl.ipp.psiutils.EquivalenceChecker;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;

class ReplaceIfWithConditionalPredicate implements PsiElementPredicate {

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
    if (isReplaceableAssignment(ifStatement)) {
      return true;
    }
    if (isReplaceableReturn(ifStatement)) {
      return true;
    }
    if (isReplaceableMethodCall(ifStatement)) {
      return true;
    }
    return isReplaceableImplicitReturn(ifStatement);
  }

  public static boolean isReplaceableMethodCall(PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    PsiStatement elseBranch = ifStatement.getElseBranch();
    PsiStatement thenStatement = ConditionalUtils.stripBraces(thenBranch);
    if (thenStatement == null) {
      return false;
    }
    PsiStatement elseStatement = ConditionalUtils.stripBraces(elseBranch);
    if (elseStatement == null) {
      return false;
    }
    if (!(thenStatement instanceof PsiExpressionStatement) || !(elseStatement instanceof PsiExpressionStatement)) {
      return false;
    }
    PsiExpressionStatement thenExpressionStatement = (PsiExpressionStatement)thenStatement;
    PsiExpression thenExpression = thenExpressionStatement.getExpression();
    PsiExpressionStatement elseExpressionStatement = (PsiExpressionStatement)elseStatement;
    PsiExpression elseExpression = elseExpressionStatement.getExpression();
    if (!(thenExpression instanceof PsiMethodCallExpression) || !(elseExpression instanceof PsiMethodCallExpression)) {
      return false;
    }
    PsiMethodCallExpression thenMethodCallExpression = (PsiMethodCallExpression)thenExpression;
    PsiMethodCallExpression elseMethodCallExpression = (PsiMethodCallExpression)elseExpression;
    PsiReferenceExpression thenMethodExpression = thenMethodCallExpression.getMethodExpression();
    PsiReferenceExpression elseMethodExpression = elseMethodCallExpression.getMethodExpression();
    if (!EquivalenceChecker.expressionsAreEquivalent(thenMethodExpression, elseMethodExpression)) {
      return false;
    }
    PsiExpressionList thenArgumentList = thenMethodCallExpression.getArgumentList();
    PsiExpression[] thenArguments = thenArgumentList.getExpressions();
    PsiExpressionList elseArgumentList = elseMethodCallExpression.getArgumentList();
    PsiExpression[] elseArguments = elseArgumentList.getExpressions();
    if (thenArguments.length != elseArguments.length) {
      return false;
    }
    int differences = 0;
    for (int i = 0, length = thenArguments.length; i < length; i++) {
      PsiExpression thenArgument = thenArguments[i];
      PsiExpression elseArgument = elseArguments[i];
      if (!EquivalenceChecker.expressionsAreEquivalent(thenArgument,  elseArgument)) {
        differences++;
      }
    }
    return differences == 1;
  }

  public static boolean isReplaceableImplicitReturn(PsiIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    if (!(thenBranch instanceof PsiReturnStatement)) {
      return false;
    }
    PsiReturnStatement thenReturnStatement = (PsiReturnStatement)thenBranch;
    PsiExpression thenReturn = thenReturnStatement.getReturnValue();
    if (thenReturn == null) {
      return false;
    }
    PsiType thenType = thenReturn.getType();
    if (thenType == null) {
      return false;
    }
    PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(ifStatement, PsiWhiteSpace.class);
    if (!(nextStatement instanceof PsiReturnStatement)) {
      return false;
    }
    PsiReturnStatement elseReturnStatement = (PsiReturnStatement)nextStatement;
    PsiExpression elseReturn = elseReturnStatement.getReturnValue();
    if (elseReturn == null) {
      return false;
    }
    PsiType elseType = elseReturn.getType();
    if (elseType == null) {
      return false;
    }
    return thenType.isAssignableFrom(elseType) || elseType.isAssignableFrom(thenType);
  }

  public static boolean isReplaceableReturn(PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ifStatement.getThenBranch();
    thenBranch = ConditionalUtils.stripBraces(thenBranch);
    PsiStatement elseBranch = ifStatement.getElseBranch();
    elseBranch = ConditionalUtils.stripBraces(elseBranch);
    if (!(thenBranch instanceof PsiReturnStatement) || !(elseBranch instanceof PsiReturnStatement)) {
      return false;
    }
    PsiExpression thenReturn = ((PsiReturnStatement)thenBranch).getReturnValue();
    if (thenReturn == null) {
      return false;
    }
    PsiExpression elseReturn = ((PsiReturnStatement)elseBranch).getReturnValue();
    if (elseReturn == null) {
      return false;
    }
    PsiType thenType = thenReturn.getType();
    PsiType elseType = elseReturn.getType();
    if (thenType == null || elseType == null) {
      return false;
    }
    return thenType.isAssignableFrom(elseType) || elseType.isAssignableFrom(thenType);
  }

  public static boolean isReplaceableAssignment(PsiIfStatement ifStatement) {
    PsiStatement thenBranch = ConditionalUtils.stripBraces(ifStatement.getThenBranch());
    PsiStatement elseBranch = ConditionalUtils.stripBraces(ifStatement.getElseBranch());
    if (thenBranch == null || elseBranch == null) {
      return false;
    }
    if (!(thenBranch instanceof PsiExpressionStatement) || !(elseBranch instanceof PsiExpressionStatement)) {
      return false;
    }
    PsiExpressionStatement thenExpressionStatement = (PsiExpressionStatement)thenBranch;
    PsiExpressionStatement elseExpressionStatement = (PsiExpressionStatement)elseBranch;
    PsiExpression thenExpression = thenExpressionStatement.getExpression();
    PsiExpression elseExpression = elseExpressionStatement.getExpression();
    if (!(thenExpression instanceof PsiAssignmentExpression) || !(elseExpression instanceof PsiAssignmentExpression)) {
      return false;
    }
    PsiAssignmentExpression thenAssignmentExpression = (PsiAssignmentExpression)thenExpression;
    PsiAssignmentExpression elseAssignmentExpression = (PsiAssignmentExpression)elseExpression;
    PsiExpression thenRhs = thenAssignmentExpression.getRExpression();
    PsiExpression elseRhs = elseAssignmentExpression.getRExpression();
    if (thenRhs == null || elseRhs == null) {
      return false;
    }
    IElementType thenTokenType = thenAssignmentExpression.getOperationTokenType();
    IElementType elseTokenType = elseAssignmentExpression.getOperationTokenType();
    if (!thenTokenType.equals(elseTokenType)) {
      return false;
    }
    PsiExpression thenLhs = thenAssignmentExpression.getLExpression();
    PsiExpression elseLhs = elseAssignmentExpression.getLExpression();
    return EquivalenceChecker.expressionsAreEquivalent(thenLhs, elseLhs);
  }
}
