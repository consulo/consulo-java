/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author mike
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.SplitIfAction", categories = {"Java", "Control Flow"}, fileExtensions = "java")
public class SplitIfAction extends PsiElementBaseIntentionAction {
  private static final Logger LOG = Logger.getInstance(SplitIfAction.class);

  public SplitIfAction() {
    setText(CodeInsightLocalize.intentionSplitIfFamily());
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    if (!(element instanceof PsiJavaToken)) {
      return false;
    }
    PsiJavaToken token = (PsiJavaToken)element;
    if (!(token.getParent() instanceof PsiPolyadicExpression)) return false;

    PsiPolyadicExpression expression = (PsiPolyadicExpression)token.getParent();
    boolean isAndExpression = expression.getOperationTokenType() == JavaTokenType.ANDAND;
    boolean isOrExpression = expression.getOperationTokenType() == JavaTokenType.OROR;
    if (!isAndExpression && !isOrExpression) return false;

    while (expression.getParent() instanceof PsiPolyadicExpression polyadicExpression) {
      expression = polyadicExpression;
      if (isAndExpression && expression.getOperationTokenType() != JavaTokenType.ANDAND) return false;
      if (isOrExpression && expression.getOperationTokenType() != JavaTokenType.OROR) return false;
    }

    if (!(expression.getParent() instanceof PsiIfStatement)) return false;
    PsiIfStatement ifStatement = (PsiIfStatement)expression.getParent();

    if (!PsiTreeUtil.isAncestor(ifStatement.getCondition(), expression, false)) return false;
    if (ifStatement.getThenBranch() == null) return false;

    setText(CodeInsightLocalize.intentionSplitIfText());

    return true;
  }

  @Override
  @RequiredReadAction
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    try {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

      PsiJavaToken token = (PsiJavaToken)element;
      LOG.assertTrue(token.getTokenType() == JavaTokenType.ANDAND || token.getTokenType() == JavaTokenType.OROR);

      PsiPolyadicExpression expression = (PsiPolyadicExpression)token.getParent();
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class);

      LOG.assertTrue(PsiTreeUtil.isAncestor(ifStatement.getCondition(), expression, false));

      if (token.getTokenType() == JavaTokenType.ANDAND) {
        doAndSplit(ifStatement, expression, token, editor);
      }
      else if (token.getTokenType() == JavaTokenType.OROR) {
        doOrSplit(ifStatement, expression, token, editor);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @RequiredReadAction
  private static void doAndSplit(PsiIfStatement ifStatement, PsiPolyadicExpression expression, PsiJavaToken token, Editor editor)
    throws IncorrectOperationException {
    PsiExpression lOperand = getLOperands(expression, token);
    PsiExpression rOperand = getROperands(expression, token);

    PsiManager psiManager = ifStatement.getManager();
    PsiIfStatement subIf = (PsiIfStatement)ifStatement.copy();

    subIf.getCondition().replace(RefactoringUtil.unparenthesizeExpression(rOperand));
    ifStatement.getCondition().replace(RefactoringUtil.unparenthesizeExpression(lOperand));

    if (ifStatement.getThenBranch() instanceof PsiBlockStatement) {
      PsiBlockStatement blockStmt = (PsiBlockStatement)JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory()
        .createStatementFromText("{}", null);
      blockStmt = (PsiBlockStatement)CodeStyleManager.getInstance(psiManager.getProject()).reformat(blockStmt);
      blockStmt = (PsiBlockStatement)ifStatement.getThenBranch().replace(blockStmt);
      blockStmt.getCodeBlock().add(subIf);
    }
    else {
      ifStatement.getThenBranch().replace(subIf);
    }

    int offset1 = ifStatement.getCondition().getTextOffset();

    editor.getCaretModel().moveToOffset(offset1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }

  @RequiredReadAction
  private static PsiExpression getROperands(PsiPolyadicExpression expression, PsiJavaToken separator) throws IncorrectOperationException {
    PsiElement next = PsiTreeUtil.skipSiblingsForward(separator, PsiWhiteSpace.class, PsiComment.class);
    int offsetInParent;
    if (next == null) {
      offsetInParent = separator.getStartOffsetInParent() + separator.getTextLength();
    } else {
      offsetInParent = next.getStartOffsetInParent();
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
    String rOperands = expression.getText().substring(offsetInParent);
    return factory.createExpressionFromText(rOperands, expression.getParent());
  }

  @RequiredReadAction
  private static PsiExpression getLOperands(PsiPolyadicExpression expression, PsiJavaToken separator) throws IncorrectOperationException {
    PsiElement prev = separator;
    if (prev.getPrevSibling() instanceof PsiWhiteSpace) prev = prev.getPrevSibling();
    if (prev == null) {
      throw new IncorrectOperationException(
        "Unable to split '" + expression.getText() + "' left to '" + separator + "'" +
          " (offset " + separator.getStartOffsetInParent() + ")"
      );
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
    String rOperands = expression.getText().substring(0, prev.getStartOffsetInParent());
    return factory.createExpressionFromText(rOperands, expression.getParent());
  }

  @RequiredReadAction
  private static void doOrSplit(
    PsiIfStatement ifStatement,
    PsiPolyadicExpression expression,
    PsiJavaToken token,
    Editor editor
  ) throws IncorrectOperationException {
    PsiExpression lOperand = getLOperands(expression, token);
    PsiExpression rOperand = getROperands(expression, token);

    PsiIfStatement secondIf = (PsiIfStatement)ifStatement.copy();

    PsiStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch != null) { elseBranch = (PsiStatement)elseBranch.copy(); }

    ifStatement.getCondition().replace(RefactoringUtil.unparenthesizeExpression(lOperand));
    secondIf.getCondition().replace(RefactoringUtil.unparenthesizeExpression(rOperand));

    ifStatement.setElseBranch(secondIf);
    if (elseBranch != null) { secondIf.setElseBranch(elseBranch); }

    int offset1 = ifStatement.getCondition().getTextOffset();

    editor.getCaretModel().moveToOffset(offset1);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    editor.getSelectionModel().removeSelection();
  }
}
