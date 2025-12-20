/*
 * Copyright 2006 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.commutative;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.SwapMethodCallArgumentsIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class SwapMethodCallArgumentsIntention extends MutablyNamedIntention {

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new SwapMethodCallArgumentsPredicate();
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.swapMethodCallArgumentsIntentionFamilyName();
    }

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        PsiExpressionList expressionList = (PsiExpressionList) element;
        PsiExpression[] expressions = expressionList.getExpressions();
        PsiExpression firstExpression = expressions[0];
        PsiExpression secondExpression = expressions[1];
        return IntentionPowerPackLocalize.swapMethodCallArgumentsIntentionName(StringUtil.first(firstExpression.getText(), 20, true), StringUtil.first(secondExpression.getText(), 20, true));
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiExpressionList argumentList = (PsiExpressionList) element;
        PsiExpression[] arguments = argumentList.getExpressions();
        PsiExpression firstArgument = arguments[0];
        PsiExpression secondArgument = arguments[1];
        String firstArgumentText = firstArgument.getText();
        String secondArgumentText = secondArgument.getText();
        PsiCallExpression callExpression =
            (PsiCallExpression) argumentList.getParent();
        @NonNls String callText;
        if (callExpression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) callExpression;
            PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
            callText = methodExpression.getText();
        }
        else if (callExpression instanceof PsiNewExpression) {
            PsiNewExpression newExpression =
                (PsiNewExpression) callExpression;
            PsiJavaCodeReferenceElement classReference =
                newExpression.getClassReference();
            assert classReference != null;
            callText = "new " + classReference.getText();
        }
        else {
            return;
        }
        String newExpression = callText + '(' + secondArgumentText +
            ", " + firstArgumentText + ')';
        replaceExpression(newExpression, callExpression);
    }
}
