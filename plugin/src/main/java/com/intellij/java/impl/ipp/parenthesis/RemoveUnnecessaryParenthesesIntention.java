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
package com.intellij.java.impl.ipp.parenthesis;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.RemoveUnnecessaryParenthesesIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class RemoveUnnecessaryParenthesesIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.removeUnnecessaryParenthesesIntentionName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new UnnecessaryParenthesesPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        if (element instanceof PsiParameterList) {
            stripLambdaParameterParentheses((PsiParameterList) element);
            return;
        }
        PsiExpression expression = (PsiExpression) element;
        ParenthesesUtils.removeParentheses(expression, false);
    }

    public static void stripLambdaParameterParentheses(PsiParameterList element) {
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
        final String text = element.getParameters()[0].getName() + "->{}";
        final PsiLambdaExpression expression = (PsiLambdaExpression) factory.createExpressionFromText(text, element);
        element.replace(expression.getParameterList());
    }
}