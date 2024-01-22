/*
 * Copyright 2009 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.opassign;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.siyeh.IntentionPowerPackBundle;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceOperatorAssignmentWithPostfixExpressionIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ReplaceOperatorAssignmentWithPostfixExpressionIntention
  extends MutablyNamedIntention {

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiAssignmentExpression assignment =
      (PsiAssignmentExpression)element;
    final PsiExpression expression = assignment.getLExpression();
    final PsiJavaToken sign = assignment.getOperationSign();
    final IElementType tokenType = sign.getTokenType();
    final String replacementText;
    if (JavaTokenType.PLUSEQ.equals(tokenType)) {
      replacementText = expression.getText() + "++";
    }
    else {
      replacementText = expression.getText() + "--";
    }
    return IntentionPowerPackBundle.message(
      "replace.some.operator.with.other.intention.name",
      sign.getText(), replacementText);
  }

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ReplaceOperatorAssignmentWithPostfixExpressionPredicate();
  }

  @Override
  protected void processIntention(@jakarta.annotation.Nonnull PsiElement element)
    throws IncorrectOperationException {
    final PsiAssignmentExpression assignment =
      (PsiAssignmentExpression)element;
    final PsiExpression expression = assignment.getLExpression();
    final String expressionText = expression.getText();
    final IElementType tokenType = assignment.getOperationTokenType();
    final String newExpressionText;
    if (JavaTokenType.PLUSEQ.equals(tokenType)) {
      newExpressionText = expressionText + "++";
    }
    else if (JavaTokenType.MINUSEQ.equals(tokenType)) {
      newExpressionText = expressionText + "--";
    }
    else {
      return;
    }
    replaceExpression(newExpressionText, assignment);
  }
}