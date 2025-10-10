/*
 * Copyright 2003-2005 Dave Griffith
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
import com.siyeh.ig.psiutils.ParenthesesUtils;
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
public class ReplaceMultiplyWithShiftIntention extends MutablyNamedIntention {

    @Nonnull
    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        if (element instanceof PsiBinaryExpression) {
            final PsiBinaryExpression exp = (PsiBinaryExpression) element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String operatorString;
            if (tokenType.equals(JavaTokenType.ASTERISK)) {
                operatorString = "<<";
            }
            else {
                operatorString = ">>";
            }
            return IntentionPowerPackLocalize.replaceSomeOperatorWithOtherIntentionName(sign.getText(), operatorString);
        }
        else {
            final PsiAssignmentExpression exp =
                (PsiAssignmentExpression) element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String assignString;
            if (tokenType.equals(JavaTokenType.ASTERISKEQ)) {
                assignString = "<<=";
            }
            else {
                assignString = ">>=";
            }
            return IntentionPowerPackLocalize.replaceSomeOperatorWithOtherIntentionName(sign.getText(), assignString);
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.replaceMultiplyWithShiftIntentionFamilyName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new MultiplyByPowerOfTwoPredicate();
    }

    @Override
    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        if (element instanceof PsiBinaryExpression) {
            replaceMultiplyOrDivideWithShift((PsiBinaryExpression) element);
        }
        else {
            replaceMultiplyOrDivideAssignWithShiftAssign(
                (PsiAssignmentExpression) element);
        }
    }

    private static void replaceMultiplyOrDivideAssignWithShiftAssign(
        PsiAssignmentExpression expression)
        throws IncorrectOperationException {
        final PsiExpression lhs = expression.getLExpression();
        final PsiExpression rhs = expression.getRExpression();
        final IElementType tokenType = expression.getOperationTokenType();
        final String assignString;
        if (tokenType.equals(JavaTokenType.ASTERISKEQ)) {
            assignString = "<<=";
        }
        else {
            assignString = ">>=";
        }
        final String expString =
            lhs.getText() + assignString + ShiftUtils.getLogBase2(rhs);
        replaceExpression(expString, expression);
    }

    private static void replaceMultiplyOrDivideWithShift(
        PsiBinaryExpression expression)
        throws IncorrectOperationException {
        final PsiExpression lhs = expression.getLOperand();
        final PsiExpression rhs = expression.getROperand();
        final IElementType tokenType = expression.getOperationTokenType();
        final String operatorString;
        if (tokenType.equals(JavaTokenType.ASTERISK)) {
            operatorString = "<<";
        }
        else {
            operatorString = ">>";
        }
        final String lhsText;
        if (ParenthesesUtils.getPrecedence(lhs) >
            ParenthesesUtils.SHIFT_PRECEDENCE) {
            lhsText = '(' + lhs.getText() + ')';
        }
        else {
            lhsText = lhs.getText();
        }
        String expString =
            lhsText + operatorString + ShiftUtils.getLogBase2(rhs);
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiExpression) {
            if (!(parent instanceof PsiParenthesizedExpression) &&
                ParenthesesUtils.getPrecedence((PsiExpression) parent) <
                    ParenthesesUtils.SHIFT_PRECEDENCE) {
                expString = '(' + expString + ')';
            }
        }
        replaceExpression(expString, expression);
    }
}