/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.shift;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
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
@IntentionMetaData(ignoreId = "java.ReplaceMultiplyWithShiftIntention", fileExtensions = "java", categories = {"Java", "Shift Operation"})
public class ReplaceShiftWithMultiplyIntention extends MutablyNamedIntention {

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.replaceMultiplyWithShiftIntentionFamilyName();
    }

    @Nonnull
    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        if (element instanceof PsiBinaryExpression) {
            final PsiBinaryExpression exp = (PsiBinaryExpression) element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String operatorString;
            if (tokenType.equals(JavaTokenType.LTLT)) {
                operatorString = "*";
            }
            else {
                operatorString = "/";
            }
            return IntentionPowerPackLocalize.replaceSomeOperatorWithOtherIntentionName(sign.getText(), operatorString);
        }
        else {
            final PsiAssignmentExpression exp =
                (PsiAssignmentExpression) element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String assignString;
            if (JavaTokenType.LTLTEQ.equals(tokenType)) {
                assignString = "*=";
            }
            else {
                assignString = "/=";
            }
            return IntentionPowerPackLocalize.replaceSomeOperatorWithOtherIntentionName(sign.getText(), assignString);
        }
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ShiftByLiteralPredicate();
    }

    @Override
    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        if (element instanceof PsiBinaryExpression) {
            replaceShiftWithMultiplyOrDivide(element);
        }
        else {
            replaceShiftAssignWithMultiplyOrDivideAssign(element);
        }
    }

    private static void replaceShiftAssignWithMultiplyOrDivideAssign(
        PsiElement element)
        throws IncorrectOperationException {
        final PsiAssignmentExpression exp =
            (PsiAssignmentExpression) element;
        final PsiExpression lhs = exp.getLExpression();
        final PsiExpression rhs = exp.getRExpression();
        final IElementType tokenType = exp.getOperationTokenType();
        final String assignString;
        if (tokenType.equals(JavaTokenType.LTLTEQ)) {
            assignString = "*=";
        }
        else {
            assignString = "/=";
        }
        final String expString =
            lhs.getText() + assignString + ShiftUtils.getExpBase2(rhs);
        replaceExpression(expString, exp);
    }

    private static void replaceShiftWithMultiplyOrDivide(PsiElement element)
        throws IncorrectOperationException {
        final PsiBinaryExpression exp =
            (PsiBinaryExpression) element;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final IElementType tokenType = exp.getOperationTokenType();
        final String operatorString;
        if (tokenType.equals(JavaTokenType.LTLT)) {
            operatorString = "*";
        }
        else {
            operatorString = "/";
        }
        final String lhsText;
        if (ParenthesesUtils.getPrecedence(lhs) >
            ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE) {
            lhsText = '(' + lhs.getText() + ')';
        }
        else {
            lhsText = lhs.getText();
        }
        String expString =
            lhsText + operatorString + ShiftUtils.getExpBase2(rhs);
        final PsiElement parent = exp.getParent();
        if (parent instanceof PsiExpression) {
            if (!(parent instanceof PsiParenthesizedExpression) &&
                ParenthesesUtils.getPrecedence((PsiExpression) parent) <
                    ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE) {
                expString = '(' + expString + ')';
            }
        }
        replaceExpression(expString, exp);
    }
}