/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.DemorgansIntention", fileExtensions = "java", categories = {
    "Java",
    "Boolean"
})
public class DemorgansIntention extends MutablyNamedIntention {

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression) element;
        IElementType tokenType = binaryExpression.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND)) {
            return IntentionPowerPackLocalize.demorgansIntentionName1();
        }
        else {
            return IntentionPowerPackLocalize.demorgansIntentionName2();
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.demorgansIntentionFamilyName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ConjunctionPredicate();
    }

    public void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) element;
        String newExpression = convertConjunctionExpression(polyadicExpression);
        replaceExpressionWithNegatedExpressionString(newExpression, polyadicExpression);
    }

    private static String convertConjunctionExpression(PsiPolyadicExpression polyadicExpression) {
        IElementType tokenType = polyadicExpression.getOperationTokenType();
        String flippedConjunction;
        boolean tokenTypeAndAnd = tokenType.equals(JavaTokenType.ANDAND);
        flippedConjunction = tokenTypeAndAnd ? "||" : "&&";
        StringBuilder result = new StringBuilder();
        for (PsiExpression operand : polyadicExpression.getOperands()) {
            if (result.length() != 0) {
                result.append(flippedConjunction);
            }
            result.append(convertLeafExpression(operand, tokenTypeAndAnd));
        }
        return result.toString();
    }

    private static String convertLeafExpression(PsiExpression expression, boolean tokenTypeAndAnd) {
        if (BoolUtils.isNegation(expression)) {
            PsiExpression negatedExpression = BoolUtils.getNegated(expression);
            if (negatedExpression == null) {
                return "";
            }
            if (tokenTypeAndAnd) {
                if (ParenthesesUtils.getPrecedence(negatedExpression) > ParenthesesUtils.OR_PRECEDENCE) {
                    return '(' + negatedExpression.getText() + ')';
                }
            }
            else if (ParenthesesUtils.getPrecedence(negatedExpression) > ParenthesesUtils.AND_PRECEDENCE) {
                return '(' + negatedExpression.getText() + ')';
            }
            return negatedExpression.getText();
        }
        else if (ComparisonUtils.isComparison(expression)) {
            PsiBinaryExpression binaryExpression = (PsiBinaryExpression) expression;
            String negatedComparison = ComparisonUtils.getNegatedComparison(binaryExpression.getOperationTokenType());
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            assert rhs != null;
            return lhs.getText() + negatedComparison + rhs.getText();
        }
        else if (ParenthesesUtils.getPrecedence(expression) > ParenthesesUtils.PREFIX_PRECEDENCE) {
            return "!(" + expression.getText() + ')';
        }
        else {
            return '!' + expression.getText();
        }
    }
}
