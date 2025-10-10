/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.FlipComparisonIntention", fileExtensions = "java", categories = {
    "Java",
    "Boolean"
})
public class FlipComparisonIntention extends MutablyNamedIntention {
    @Override
    public LocalizeValue getTextForElement(PsiElement element) {
        String operatorText = "";
        String flippedOperatorText = "";
        final PsiBinaryExpression expression = (PsiBinaryExpression) element;
        if (expression != null) {
            final PsiJavaToken sign = expression.getOperationSign();
            operatorText = sign.getText();
            flippedOperatorText = ComparisonUtils.getFlippedComparison(sign.getTokenType());
        }
        if (operatorText.equals(flippedOperatorText)) {
            return IntentionPowerPackLocalize.flipSmthIntentionName(operatorText);
        }
        else {
            return IntentionPowerPackLocalize.flipComparisonIntentionName(operatorText, flippedOperatorText);
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.flipComparisonIntentionFamilyName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ComparisonPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        final PsiBinaryExpression expression = (PsiBinaryExpression) element;
        final PsiExpression lhs = expression.getLOperand();
        final PsiExpression rhs = expression.getROperand();
        final IElementType tokenType = expression.getOperationTokenType();
        assert rhs != null;
        final String expString = rhs.getText() +
            ComparisonUtils.getFlippedComparison(tokenType) +
            lhs.getText();
        PsiReplacementUtil.replaceExpression(expression, expString);
    }
}