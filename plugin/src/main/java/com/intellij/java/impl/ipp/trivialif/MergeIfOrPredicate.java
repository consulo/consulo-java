/*
 * Copyright 2003-2012 Dave Griffith
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

import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiStatement;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.EquivalenceChecker;
import com.intellij.java.impl.ipp.psiutils.ErrorUtil;

class MergeIfOrPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    return isMergableExplicitIf(element) || isMergableImplicitIf(element);
  }

  public static boolean isMergableExplicitIf(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    PsiJavaToken token = (PsiJavaToken)element;
    PsiElement parent = token.getParent();
    if (!(parent instanceof PsiIfStatement)) {
      return false;
    }
    PsiIfStatement ifStatement = (PsiIfStatement)parent;
    PsiStatement thenBranch = ifStatement.getThenBranch();
    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (thenBranch == null) {
      return false;
    }
    if (elseBranch == null) {
      return false;
    }
    if (!(elseBranch instanceof PsiIfStatement)) {
      return false;
    }
    if (ErrorUtil.containsError(ifStatement)) {
      return false;
    }
    PsiIfStatement childIfStatement = (PsiIfStatement)elseBranch;
    PsiStatement childThenBranch = childIfStatement.getThenBranch();
    return EquivalenceChecker.statementsAreEquivalent(thenBranch, childThenBranch);
  }

  private static boolean isMergableImplicitIf(PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    PsiJavaToken token = (PsiJavaToken)element;

    PsiElement parent = token.getParent();
    if (!(parent instanceof PsiIfStatement)) {
      return false;
    }
    PsiIfStatement ifStatement = (PsiIfStatement)parent;
    PsiStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch == null) {
      return false;
    }
    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) {
      return false;
    }
    if (ControlFlowUtils.statementMayCompleteNormally(thenBranch)) {
      return false;
    }
    PsiElement nextStatement = PsiTreeUtil.skipSiblingsForward(ifStatement, PsiWhiteSpace.class);
    if (!(nextStatement instanceof PsiIfStatement)) {
      return false;
    }
    PsiIfStatement nextIfStatement = (PsiIfStatement)nextStatement;
    PsiStatement nextThenBranch = nextIfStatement.getThenBranch();
    return EquivalenceChecker.statementsAreEquivalent(thenBranch, nextThenBranch);
  }
}
