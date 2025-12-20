/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiJavaToken;
import com.intellij.java.language.psi.PsiPolyadicExpression;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceWithOperatorAssignmentIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class ReplaceWithOperatorAssignmentIntention extends MutablyNamedIntention {

    @Nonnull
    @Override
    public LocalizeValue getTextForElement(PsiElement element) {
        PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) element;
        PsiExpression rhs = assignmentExpression.getRExpression();
        PsiPolyadicExpression expression = (PsiPolyadicExpression) PsiUtil.deparenthesizeExpression(rhs);
        assert expression != null;
        PsiJavaToken sign = expression.getTokenBeforeOperand(expression.getOperands()[1]);
        assert sign != null;
        String operator = sign.getText();
        return IntentionPowerPackLocalize.replaceAssignmentWithOperatorAssignmentIntentionName(operator);
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.replaceWithOperatorAssignmentIntentionFamilyName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ReplaceableWithOperatorAssignmentPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) {
        PsiAssignmentExpression expression = (PsiAssignmentExpression) element;
        PsiExpression rhs = expression.getRExpression();
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) PsiUtil.deparenthesizeExpression(rhs);
        assert polyadicExpression != null;
        PsiExpression lhs = expression.getLExpression();
        assert rhs != null;
        PsiExpression[] operands = polyadicExpression.getOperands();
        PsiJavaToken sign = polyadicExpression.getTokenBeforeOperand(operands[1]);
        assert sign != null;
        String signText = sign.getText();
        StringBuilder newExpression = new StringBuilder();
        newExpression.append(lhs.getText()).append(signText).append('=');
        boolean token = false;
        for (int i = 1; i < operands.length; i++) {
            PsiExpression operand = operands[i];
            if (token) {
                newExpression.append(signText);
            }
            else {
                token = true;
            }
            newExpression.append(operand.getText());
        }
        replaceExpression(newExpression.toString(), expression);
    }
}