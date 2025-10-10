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
package com.intellij.java.impl.ipp.conditional;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.RemoveConditionalIntention", fileExtensions = "java", categories = {"Java", "Conditional Operator"})
public class RemoveConditionalIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.removeConditionalIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new RemoveConditionalPredicate();
    }

    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        final PsiConditionalExpression expression =
            (PsiConditionalExpression) element;
        final PsiExpression condition = expression.getCondition();
        final PsiExpression thenExpression = expression.getThenExpression();
        assert thenExpression != null;
        @NonNls final String thenExpressionText = thenExpression.getText();
        if ("true".equals(thenExpressionText)) {
            final String newExpression = condition.getText();
            replaceExpression(newExpression, expression);
        }
        else {
            final String newExpression =
                BoolUtils.getNegatedExpressionText(condition);
            replaceExpression(newExpression, expression);
        }
    }
}