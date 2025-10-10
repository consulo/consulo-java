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
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiPostfixExpression;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplacePostfixExpressionWithOperatorAssignmentIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ReplacePostfixExpressionWithOperatorAssignmentIntention
    extends MutablyNamedIntention {

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.replacePostfixExpressionWithOperatorAssignmentIntentionFamilyName();
    }

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        final PsiPostfixExpression postfixExpression =
            (PsiPostfixExpression) element;
        final PsiJavaToken sign = postfixExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        final String replacementText;
        if (JavaTokenType.PLUSPLUS.equals(tokenType)) {
            replacementText = "+=";
        }
        else {
            replacementText = "-=";
        }
        final String signText = sign.getText();
        return IntentionPowerPackLocalize.replaceSomeOperatorWithOtherIntentionName(signText, replacementText);
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ReplacePostfixExpressionWithOperatorAssignmentPredicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        final PsiPostfixExpression postfixExpression =
            (PsiPostfixExpression) element;
        final PsiExpression operand = postfixExpression.getOperand();
        final String operandText = operand.getText();
        final IElementType tokenType =
            postfixExpression.getOperationTokenType();
        if (JavaTokenType.PLUSPLUS.equals(tokenType)) {
            replaceExpression(operandText + "+=1", postfixExpression);
        }
        else if (JavaTokenType.MINUSMINUS.equals(tokenType)) {
            replaceExpression(operandText + "-=1", postfixExpression);
        }
    }
}