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
package com.intellij.java.impl.ipp.bool;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.ComparisonUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.NegateComparisonIntention", fileExtensions = "java", categories = {
		"Java",
		"Boolean"
})
public class NegateComparisonIntention extends MutablyNamedIntention {

  public String getTextForElement(PsiElement element) {
    String operatorText = "";
    String negatedOperatorText = "";
    final PsiBinaryExpression exp = (PsiBinaryExpression)element;
    if (exp != null) {
      final PsiJavaToken sign = exp.getOperationSign();
      operatorText = sign.getText();
      negatedOperatorText =
        ComparisonUtils.getNegatedComparison(sign.getTokenType());
    }
    if (operatorText.equals(negatedOperatorText)) {
      return IntentionPowerPackBundle.message(
        "negate.comparison.intention.name", operatorText);
    }
    else {
      return IntentionPowerPackBundle.message(
        "negate.comparison.intention.name1", operatorText,
        negatedOperatorText);
    }
  }

  @Nonnull
  public PsiElementPredicate getElementPredicate() {
    return new ComparisonPredicate();
  }

  public void processIntention(PsiElement element)
    throws IncorrectOperationException {
    final PsiBinaryExpression expression =
      (PsiBinaryExpression)element;
    final PsiExpression lhs = expression.getLOperand();
    final PsiExpression rhs = expression.getROperand();
    final String negatedOperator =
      ComparisonUtils.getNegatedComparison(expression.getOperationTokenType());
    final String lhsText = lhs.getText();
    assert rhs != null;
    final String rhsText = rhs.getText();
    replaceExpressionWithNegatedExpressionString(lhsText +
                                                 negatedOperator + rhsText, expression);
  }
}