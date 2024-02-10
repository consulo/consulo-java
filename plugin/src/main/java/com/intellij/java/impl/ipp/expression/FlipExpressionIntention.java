/*
 * Copyright 2007-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.expression;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ConcatenationUtils;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.CaretModel;
import consulo.codeEditor.Editor;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.FlipExpressionIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class FlipExpressionIntention extends MutablyNamedIntention {

  @Override
  public String getTextForElement(PsiElement element) {
    final PsiPolyadicExpression expression = (PsiPolyadicExpression)element.getParent();
    final PsiExpression[] operands = expression.getOperands();
    final PsiJavaToken sign = expression.getTokenBeforeOperand(operands[1]);
    final String operatorText = sign == null ? "" : sign.getText();
    final IElementType tokenType = expression.getOperationTokenType();
    final boolean commutative = ParenthesesUtils.isCommutativeOperator(tokenType);
    if (commutative && !ConcatenationUtils.isConcatenation(expression)) {
      return IntentionPowerPackBundle.message("flip.smth.intention.name", operatorText);
    }
    else {
      return IntentionPowerPackBundle.message("flip.smth.intention.name1", operatorText);
    }
  }

  @Override
  @Nonnull
  public PsiElementPredicate getElementPredicate() {
    return new ExpressionPredicate();
  }

  @Override
  public void processIntention(@Nonnull PsiElement element) {
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiElement parent = token.getParent();
    if (!(parent instanceof PsiPolyadicExpression)) {
      return;
    }
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
    final PsiExpression[] operands = polyadicExpression.getOperands();
    final StringBuilder newExpression = new StringBuilder();
    String prevOperand = null;
    final String tokenText = token.getText() + ' '; // 2- -1 without the space is not legal
    for (PsiExpression operand : operands) {
      final PsiJavaToken token1 = polyadicExpression.getTokenBeforeOperand(operand);
      if (token == token1) {
        newExpression.append(operand.getText()).append(tokenText);
        continue;
      }
      if (prevOperand != null) {
        newExpression.append(prevOperand).append(tokenText);
      }
      prevOperand = operand.getText();
    }
    newExpression.append(prevOperand);
    replaceExpression(newExpression.toString(), polyadicExpression);
  }

  @Override
  protected void processIntention(Editor editor, @Nonnull PsiElement element) {
    final CaretModel caretModel = editor.getCaretModel();
    final int offset = caretModel.getOffset();
    super.processIntention(editor, element);
    caretModel.moveToOffset(offset);
  }
}