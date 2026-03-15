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
package com.intellij.java.impl.codeInsight.intention.impl;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiExpressionTrimRenderer;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * @author Danila Ponomarenko
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ExtractIfConditionAction", categories = {"Java", "Control Flow"}, fileExtensions = "java")
public class ExtractIfConditionAction extends PsiElementBaseIntentionAction {
  public ExtractIfConditionAction() {
    setText(CodeInsightLocalize.intentionExtractIfConditionFamily());
  }

  @Override
  @RequiredReadAction
  public boolean isAvailable(Project project, Editor editor, PsiElement element) {
    PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null || ifStatement.getCondition() == null) {
      return false;
    }

    PsiExpression condition = ifStatement.getCondition();

    if (condition == null || !(condition instanceof PsiPolyadicExpression)) {
      return false;
    }

    PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;
    PsiType expressionType = polyadicExpression.getType();
    if (expressionType == null || !PsiType.BOOLEAN.isAssignableFrom(expressionType)) {
      return false;
    }

    IElementType operation = polyadicExpression.getOperationTokenType();

    if (operation != JavaTokenType.OROR && operation != JavaTokenType.ANDAND) {
      return false;
    }

    PsiExpression operand = findOperand(element, polyadicExpression);

    if (operand == null) {
      return false;
    }
    setText(CodeInsightLocalize.intentionExtractIfConditionText(PsiExpressionTrimRenderer.render(operand)));
    return true;
  }

  @Override
  @RequiredReadAction
  public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null) {
      return;
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    PsiStatement newIfStatement = create(factory, ifStatement, element);
    if (newIfStatement == null) {
      return;
    }

    ifStatement.replace(codeStyleManager.reformat(newIfStatement));
  }

  @Nullable
  @RequiredReadAction
  private static PsiStatement create(PsiElementFactory factory, PsiIfStatement ifStatement, PsiElement element) {
    PsiExpression condition = ifStatement.getCondition();

    if (condition == null || !(condition instanceof PsiPolyadicExpression)) {
      return null;
    }

    PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;

    PsiExpression operand = findOperand(element, polyadicExpression);

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

  @RequiredReadAction
  private static PsiExpression removeOperand(
    PsiElementFactory factory,
    PsiPolyadicExpression expression,
    PsiExpression operand
  ) {
    StringBuilder sb = new StringBuilder();
    for (PsiExpression e : expression.getOperands()) {
      if (e == operand) continue;
      PsiJavaToken token = expression.getTokenBeforeOperand(e);
      if (token != null && sb.length() != 0) {
        sb.append(token.getText()).append(" ");
      }
      sb.append(e.getText());
    }
    return factory.createExpressionFromText(sb.toString(), expression);
  }

  @Nullable
  @RequiredReadAction
  private static PsiStatement create(
    PsiElementFactory factory,
    @Nullable PsiStatement thenBranch,
    @Nullable PsiStatement elseBranch,
    PsiExpression extract,
    PsiExpression leave,
    IElementType operation
  ) {
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

  @RequiredReadAction
  private static PsiStatement createAndAnd(
    PsiElementFactory factory,
    PsiStatement thenBranch,
    @Nullable PsiStatement elseBranch,
    PsiExpression extract,
    PsiExpression leave
  ) {
    return factory.createStatementFromText(
      createIfString(extract, createIfString(leave, thenBranch, elseBranch), elseBranch),
      thenBranch
    );
  }

  @RequiredReadAction
  private static PsiStatement createOrOr(
    PsiElementFactory factory,
    PsiStatement thenBranch,
    @Nullable PsiStatement elseBranch,
    PsiExpression extract,
    PsiExpression leave
  ) {
    return factory.createStatementFromText(
      createIfString(extract, thenBranch, createIfString(leave, thenBranch, elseBranch)),
      thenBranch
    );
  }

  @RequiredReadAction
  private static String createIfString(
    PsiExpression condition,
    PsiStatement thenBranch,
    @Nullable PsiStatement elseBranch
  ) {
    return createIfString(condition.getText(), toThenBranchString(thenBranch), toElseBranchString(elseBranch));
  }

  @RequiredReadAction
  private static String createIfString(PsiExpression condition, PsiStatement thenBranch, @Nullable String elseBranch) {
    return createIfString(condition.getText(), toThenBranchString(thenBranch), elseBranch);
  }

  @RequiredReadAction
  private static String createIfString(PsiExpression condition, String thenBranch, @Nullable PsiStatement elseBranch) {
    return createIfString(condition.getText(), thenBranch, toElseBranchString(elseBranch));
  }

  private static String createIfString(String condition, String thenBranch, @Nullable String elseBranch) {
    String elsePart = elseBranch != null ? " else " + elseBranch : "";
    return "if (" + condition + ")\n" + thenBranch + elsePart;
  }

  @RequiredReadAction
  private static String toThenBranchString(PsiStatement statement) {
    return statement instanceof PsiBlockStatement ? statement.getText() : "{ " + statement.getText() + " }";
  }

  @Nullable
  @RequiredReadAction
  private static String toElseBranchString(@Nullable PsiStatement statement) {
    if (statement == null) {
      return null;
    }

    return statement instanceof PsiBlockStatement || statement instanceof PsiIfStatement
      ? statement.getText() : "{ " + statement.getText() + " }";
  }

  @Nullable
  @RequiredReadAction
  private static PsiExpression findOperand(PsiElement e, PsiPolyadicExpression expression) {
    TextRange elementTextRange = e.getTextRange();

    for (PsiExpression operand : expression.getOperands()) {
      TextRange operandTextRange = operand.getTextRange();
      if (operandTextRange != null && operandTextRange.contains(elementTextRange)) {
        return operand;
      }
    }
    return null;
  }
}
