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
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceOperatorAssignmentWithPostfixExpressionIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ReplaceOperatorAssignmentWithPostfixExpressionIntention
    extends MutablyNamedIntention {

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.replaceWithOperatorAssignmentIntentionFamilyName();
    }

    @Nonnull
    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        PsiAssignmentExpression assignment =
            (PsiAssignmentExpression) element;
        PsiExpression expression = assignment.getLExpression();
        PsiJavaToken sign = assignment.getOperationSign();
        IElementType tokenType = sign.getTokenType();
        String replacementText;
        if (JavaTokenType.PLUSEQ.equals(tokenType)) {
            replacementText = expression.getText() + "++";
        }
        else {
            replacementText = expression.getText() + "--";
        }
        return IntentionPowerPackLocalize.replaceSomeOperatorWithOtherIntentionName(sign.getText(), replacementText);
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ReplaceOperatorAssignmentWithPostfixExpressionPredicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiAssignmentExpression assignment =
            (PsiAssignmentExpression) element;
        PsiExpression expression = assignment.getLExpression();
        String expressionText = expression.getText();
        IElementType tokenType = assignment.getOperationTokenType();
        String newExpressionText;
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