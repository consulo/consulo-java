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
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.RemoveBooleanEqualityIntention", fileExtensions = "java", categories = {
    "Java",
    "Boolean"
})
public class RemoveBooleanEqualityIntention extends MutablyNamedIntention {

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        final PsiBinaryExpression binaryExpression =
            (PsiBinaryExpression) element;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        return IntentionPowerPackLocalize.removeBooleanEqualityIntentionName(sign.getText());
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.removeBooleanEqualityIntentionFamilyName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new BooleanLiteralEqualityPredicate();
    }

    @Override
    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        final PsiBinaryExpression exp =
            (PsiBinaryExpression) element;
        assert exp != null;
        final IElementType tokenType = exp.getOperationTokenType();
        final boolean isEquals = JavaTokenType.EQEQ.equals(tokenType);
        final PsiExpression lhs = exp.getLOperand();
        @NonNls final String lhsText = lhs.getText();
        final PsiExpression rhs = exp.getROperand();
        assert rhs != null;
        @NonNls final String rhsText = rhs.getText();
        if (PsiKeyword.TRUE.equals(lhsText)) {
            if (isEquals) {
                replaceExpression(rhsText, exp);
            }
            else {
                replaceExpressionWithNegatedExpression(rhs, exp);
            }
        }
        else if (PsiKeyword.FALSE.equals(lhsText)) {
            if (isEquals) {
                replaceExpressionWithNegatedExpression(rhs, exp);
            }
            else {
                replaceExpression(rhsText, exp);
            }
        }
        else if (PsiKeyword.TRUE.equals(rhsText)) {
            if (isEquals) {
                replaceExpression(lhsText, exp);
            }
            else {
                replaceExpressionWithNegatedExpression(lhs, exp);
            }
        }
        else {
            if (isEquals) {
                replaceExpressionWithNegatedExpression(lhs, exp);
            }
            else {
                replaceExpression(lhsText, exp);
            }
        }
    }
}