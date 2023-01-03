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
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.siyeh.IntentionPowerPackBundle;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

import javax.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceAssignmentWithPostfixExpressionIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ReplaceAssignmentWithPostfixExpressionIntention
  extends MutablyNamedIntention {

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ReplaceAssignmentWithPostfixExpressionPredicate();
  }

  @Override
  protected String getTextForElement(PsiElement element) {
    final PsiAssignmentExpression assignmentExpression =
      (PsiAssignmentExpression)element;
    final PsiBinaryExpression rhs =
      (PsiBinaryExpression)assignmentExpression.getRExpression();
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final String lhsText = lhs.getText();
    final IElementType tokenType;
    if (rhs == null) {
      tokenType = null;
    }
    else {
      tokenType = rhs.getOperationTokenType();
    }
    final String replacementText;
    if (JavaTokenType.MINUS.equals(tokenType)) {
      replacementText = lhsText + "--";
    }
    else {
      replacementText = lhsText + "++";
    }
    return IntentionPowerPackBundle.message(
      "replace.some.operator.with.other.intention.name", "=",
      replacementText);
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element)
    throws IncorrectOperationException {
    final PsiAssignmentExpression assignmentExpression =
      (PsiAssignmentExpression)element;
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final String lhsText = lhs.getText();
    final PsiExpression rhs = assignmentExpression.getRExpression();
    if (!(rhs instanceof PsiBinaryExpression)) {
      return;
    }
    final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)rhs;
    final IElementType tokenType = binaryExpression.getOperationTokenType();
    if (JavaTokenType.PLUS.equals(tokenType)) {
      replaceExpression(lhsText + "++", assignmentExpression);
    }
    else if (JavaTokenType.MINUS.equals(tokenType)) {
      replaceExpression(lhsText + "--", assignmentExpression);
    }
  }
}