/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiIfStatement;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiStatement;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MergeIfOrIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class MergeIfOrIntention extends Intention {

  @Nonnull
  public PsiElementPredicate getElementPredicate() {
    return new MergeIfOrPredicate();
  }

  public void processIntention(PsiElement element)
    throws IncorrectOperationException {
    final PsiJavaToken token = (PsiJavaToken)element;
    if (MergeIfOrPredicate.isMergableExplicitIf(token)) {
      replaceMergeableExplicitIf(token);
    }
    else {
      replaceMergeableImplicitIf(token);
    }
  }

  private static void replaceMergeableExplicitIf(PsiJavaToken token)
    throws IncorrectOperationException {
    final PsiIfStatement parentStatement =
      (PsiIfStatement)token.getParent();
    assert parentStatement != null;
    final PsiIfStatement childStatement =
      (PsiIfStatement)parentStatement.getElseBranch();
    if (childStatement == null) {
      return;
    }
    final PsiExpression childCondition = childStatement.getCondition();
    if (childCondition == null) {
      return;
    }
    final String childConditionText;
    if (ParenthesesUtils.getPrecedence(childCondition)
        > ParenthesesUtils.OR_PRECEDENCE) {
      childConditionText = '(' + childCondition.getText() + ')';
    }
    else {
      childConditionText = childCondition.getText();
    }
    final PsiExpression condition = parentStatement.getCondition();
    if (condition == null) {
      return;
    }
    final String parentConditionText;
    if (ParenthesesUtils.getPrecedence(condition)
        > ParenthesesUtils.OR_PRECEDENCE) {
      parentConditionText = '(' + condition.getText() + ')';
    }
    else {
      parentConditionText = condition.getText();
    }
    final PsiStatement parentThenBranch = parentStatement.getThenBranch();
    if (parentThenBranch == null) {
      return;
    }
    final String parentThenBranchText = parentThenBranch.getText();
    @NonNls final StringBuilder statement = new StringBuilder();
    statement.append("if(");
    statement.append(parentConditionText);
    statement.append("||");
    statement.append(childConditionText);
    statement.append(')');
    statement.append(parentThenBranchText);
    final PsiStatement childElseBranch = childStatement.getElseBranch();
    if (childElseBranch != null) {
      final String childElseBranchText = childElseBranch.getText();
      statement.append("else ");
      statement.append(childElseBranchText);
    }
    final String newStatement = statement.toString();
    replaceStatement(newStatement, parentStatement);
  }

  private static void replaceMergeableImplicitIf(PsiJavaToken token)
    throws IncorrectOperationException {
    final PsiIfStatement parentStatement =
      (PsiIfStatement)token.getParent();
    final PsiIfStatement childStatement =
      (PsiIfStatement)PsiTreeUtil.skipSiblingsForward(parentStatement,
                                                      PsiWhiteSpace.class);
    assert childStatement != null;
    final PsiExpression childCondition = childStatement.getCondition();
    if (childCondition == null) {
      return;
    }
    final String childConditionText;
    if (ParenthesesUtils.getPrecedence(childCondition)
        > ParenthesesUtils.OR_PRECEDENCE) {
      childConditionText = '(' + childCondition.getText() + ')';
    }
    else {
      childConditionText = childCondition.getText();
    }
    assert parentStatement != null;
    final PsiExpression condition = parentStatement.getCondition();
    if (condition == null) {
      return;
    }
    final String parentConditionText;
    if (ParenthesesUtils.getPrecedence(condition)
        > ParenthesesUtils.OR_PRECEDENCE) {
      parentConditionText = '(' + condition.getText() + ')';
    }
    else {
      parentConditionText = condition.getText();
    }
    final PsiStatement parentThenBranch = parentStatement.getThenBranch();
    if (parentThenBranch == null) {
      return;
    }
    final StringBuilder newStatement = new StringBuilder();
    newStatement.append("if(");
    newStatement.append(parentConditionText);
    newStatement.append("||");
    newStatement.append(childConditionText);
    newStatement.append(')');
    newStatement.append(parentThenBranch.getText());
    final PsiStatement childElseBranch = childStatement.getElseBranch();
    if (childElseBranch != null) {
      newStatement.append("else ");
      newStatement.append(childElseBranch.getText());
    }
    replaceStatement(newStatement.toString(), parentStatement);
    childStatement.delete();
  }
}