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
            PsiBinaryExpression exp = (PsiBinaryExpression) element;
            PsiJavaToken sign = exp.getOperationSign();
            IElementType tokenType = sign.getTokenType();
            String operatorString;
            if (tokenType.equals(JavaTokenType.LTLT)) {
                operatorString = "*";
            }
            else {
                operatorString = "/";
            }
            return IntentionPowerPackLocalize.replaceSomeOperatorWithOtherIntentionName(sign.getText(), operatorString);
        }
        else {
            PsiAssignmentExpression exp =
                (PsiAssignmentExpression) element;
            PsiJavaToken sign = exp.getOperationSign();
            IElementType tokenType = sign.getTokenType();
            String assignString;
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
        PsiAssignmentExpression exp =
            (PsiAssignmentExpression) element;
        PsiExpression lhs = exp.getLExpression();
        PsiExpression rhs = exp.getRExpression();
        IElementType tokenType = exp.getOperationTokenType();
        String assignString;
        if (tokenType.equals(JavaTokenType.LTLTEQ)) {
            assignString = "*=";
        }
        else {
            assignString = "/=";
        }
        String expString =
            lhs.getText() + assignString + ShiftUtils.getExpBase2(rhs);
        replaceExpression(expString, exp);
    }

    private static void replaceShiftWithMultiplyOrDivide(PsiElement element)
        throws IncorrectOperationException {
        PsiBinaryExpression exp =
            (PsiBinaryExpression) element;
        PsiExpression lhs = exp.getLOperand();
        PsiExpression rhs = exp.getROperand();
        IElementType tokenType = exp.getOperationTokenType();
        String operatorString;
        if (tokenType.equals(JavaTokenType.LTLT)) {
            operatorString = "*";
        }
        else {
            operatorString = "/";
        }
        String lhsText;
        if (ParenthesesUtils.getPrecedence(lhs) >
            ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE) {
            lhsText = '(' + lhs.getText() + ')';
        }
        else {
            lhsText = lhs.getText();
        }
        String expString =
            lhsText + operatorString + ShiftUtils.getExpBase2(rhs);
        PsiElement parent = exp.getParent();
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