/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.language.psi.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Danila Ponomarenko
 */
public class ExtractIfConditionAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null || ifStatement.getCondition() == null) {
      return false;
    }

    final PsiExpression condition = ifStatement.getCondition();

    if (condition == null || !(condition instanceof PsiPolyadicExpression)) {
      return false;
    }

    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;
    final PsiType expressionType = polyadicExpression.getType();
    if (expressionType == null || !PsiType.BOOLEAN.isAssignableFrom(expressionType)) {
      return false;
    }

    final IElementType operation = polyadicExpression.getOperationTokenType();

    if (operation != JavaTokenType.OROR && operation != JavaTokenType.ANDAND) {
      return false;
    }

    final PsiExpression operand = findOperand(element, polyadicExpression);

    if (operand == null) {
      return false;
    }
    setText(CodeInsightBundle.message("intention.extract.if.condition.text", PsiExpressionTrimRenderer.render(operand)));
    return true;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null) {
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    final PsiStatement newIfStatement = create(factory, ifStatement, element);
    if (newIfStatement == null) {
      return;
    }

    ifStatement.replace(codeStyleManager.reformat(newIfStatement));
  }

  @javax.annotation.Nullable
  private static PsiStatement create(@Nonnull PsiElementFactory factory,
                                     @Nonnull PsiIfStatement ifStatement,
                                     @Nonnull PsiElement element) {

    final PsiExpression condition = ifStatement.getCondition();

    if (condition == null || !(condition instanceof PsiPolyadicExpression)) {
      return null;
    }

    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;

    final PsiExpression operand = findOperand(element, polyadicExpression);

    if (operand == null) {
      return null;
    }


    return create(
      factory,
      ifStatement.getThenBranch(), ifStatement.getElseBranch(),
      operand,
      removeOperand(factory, polyadicExpression, operand),
      polyadicExpression.getOperationTokenType()
    );
  }

  @Nonnull
  private static PsiExpression removeOperand(@Nonnull PsiElementFactory factory,
                                             @Nonnull PsiPolyadicExpression expression,
                                             @Nonnull PsiExpression operand) {
    final StringBuilder sb = new StringBuilder();
    for (PsiExpression e : expression.getOperands()) {
      if (e == operand) continue;
      final PsiJavaToken token = expression.getTokenBeforeOperand(e);
      if (token != null && sb.length() != 0) {
        sb.append(token.getText()).append(" ");
      }
      sb.append(e.getText());
    }
    return factory.createExpressionFromText(sb.toString(), expression);
  }

  @javax.annotation.Nullable
  private static PsiStatement create(@Nonnull PsiElementFactory factory,
                                     @javax.annotation.Nullable PsiStatement thenBranch,
                                     @javax.annotation.Nullable PsiStatement elseBranch,
                                     @Nonnull PsiExpression extract,
                                     @Nonnull PsiExpression leave,
                                     @Nonnull IElementType operation) {
    if (thenBranch == null) {
      return null;
    }

    if (operation == JavaTokenType.OROR) {
      return createOrOr(factory, thenBranch, elseBranch, extract, leave);
    }
    if (operation == JavaTokenType.ANDAND) {
      return createAndAnd(factory, thenBranch, elseBranch, extract, leave);
    }

    return null;
  }

  @Nonnull
  private static PsiStatement createAndAnd(@Nonnull PsiElementFactory factory,
                                           @Nonnull PsiStatement thenBranch,
                                           @javax.annotation.Nullable PsiStatement elseBranch,
                                           @Nonnull PsiExpression extract,
                                           @Nonnull PsiExpression leave) {

    return factory.createStatementFromText(
      createIfString(extract,
                     createIfString(leave, thenBranch, elseBranch),
                     elseBranch
      ),
      thenBranch
    );
  }

  @Nonnull
  private static PsiStatement createOrOr(@Nonnull PsiElementFactory factory,
                                         @Nonnull PsiStatement thenBranch,
                                         @javax.annotation.Nullable PsiStatement elseBranch,
                                         @Nonnull PsiExpression extract,
                                         @Nonnull PsiExpression leave) {

    return factory.createStatementFromText(
      createIfString(extract, thenBranch,
                     createIfString(leave, thenBranch, elseBranch)
      ),
      thenBranch
    );
  }

  @Nonnull
  private static String createIfString(@Nonnull PsiExpression condition,
                                       @Nonnull PsiStatement thenBranch,
                                       @javax.annotation.Nullable PsiStatement elseBranch) {
    return createIfString(condition.getText(), toThenBranchString(thenBranch), toElseBranchString(elseBranch));
  }

  @Nonnull
  private static String createIfString(@Nonnull PsiExpression condition,
                                       @Nonnull PsiStatement thenBranch,
                                       @javax.annotation.Nullable String elseBranch) {
    return createIfString(condition.getText(), toThenBranchString(thenBranch), elseBranch);
  }

  @Nonnull
  private static String createIfString(@Nonnull PsiExpression condition,
                                       @Nonnull String thenBranch,
                                       @javax.annotation.Nullable PsiStatement elseBranch) {
    return createIfString(condition.getText(), thenBranch, toElseBranchString(elseBranch));
  }

  @Nonnull
  private static String createIfString(@Nonnull String condition,
                                       @Nonnull String thenBranch,
                                       @javax.annotation.Nullable String elseBranch) {
    final String elsePart = elseBranch != null ? " else " + elseBranch : "";
    return "if (" + condition + ")\n" + thenBranch + elsePart;
  }

  @Nonnull
  private static String toThenBranchString(@Nonnull PsiStatement statement) {
    if (!(statement instanceof PsiBlockStatement)) {
      return "{ " + statement.getText() + " }";
    }

    return statement.getText();
  }

  @javax.annotation.Nullable
  private static String toElseBranchString(@javax.annotation.Nullable PsiStatement statement) {
    if (statement == null) {
      return null;
    }

    if (statement instanceof PsiBlockStatement || statement instanceof PsiIfStatement) {
      return statement.getText();
    }

    return "{ " + statement.getText() + " }";
  }

  @javax.annotation.Nullable
  private static PsiExpression findOperand(@Nonnull PsiElement e, @Nonnull PsiPolyadicExpression expression) {
    final TextRange elementTextRange = e.getTextRange();

    for (PsiExpression operand : expression.getOperands()) {
      final TextRange operandTextRange = operand.getTextRange();
      if (operandTextRange != null && operandTextRange.contains(elementTextRange)) {
        return operand;
      }
    }
    return null;
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.extract.if.condition.family");
  }
}
