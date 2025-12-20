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
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceAssignmentWithPostfixExpressionIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ReplaceAssignmentWithPostfixExpressionIntention
    extends MutablyNamedIntention {

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ReplaceAssignmentWithPostfixExpressionPredicate();
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.replaceAssignmentWithPostfixExpressionIntentionFamilyName();
    }

    @Nonnull
    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        PsiAssignmentExpression assignmentExpression =
            (PsiAssignmentExpression) element;
        PsiBinaryExpression rhs =
            (PsiBinaryExpression) assignmentExpression.getRExpression();
        PsiExpression lhs = assignmentExpression.getLExpression();
        String lhsText = lhs.getText();
        IElementType tokenType;
        if (rhs == null) {
            tokenType = null;
        }
        else {
            tokenType = rhs.getOperationTokenType();
        }
        String replacementText;
        if (JavaTokenType.MINUS.equals(tokenType)) {
            replacementText = lhsText + "--";
        }
        else {
            replacementText = lhsText + "++";
        }
        return IntentionPowerPackLocalize.replaceSomeOperatorWithOtherIntentionName("=", replacementText);
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiAssignmentExpression assignmentExpression =
            (PsiAssignmentExpression) element;
        PsiExpression lhs = assignmentExpression.getLExpression();
        String lhsText = lhs.getText();
        PsiExpression rhs = assignmentExpression.getRExpression();
        if (!(rhs instanceof PsiBinaryExpression)) {
            return;
        }
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression) rhs;
        IElementType tokenType = binaryExpression.getOperationTokenType();
        if (JavaTokenType.PLUS.equals(tokenType)) {
            replaceExpression(lhsText + "++", assignmentExpression);
        }
        else if (JavaTokenType.MINUS.equals(tokenType)) {
            replaceExpression(lhsText + "--", assignmentExpression);
        }
    }
}