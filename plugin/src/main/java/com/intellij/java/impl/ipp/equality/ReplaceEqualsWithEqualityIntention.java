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
package com.intellij.java.impl.ipp.equality;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceEqualsWithEqualityIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class ReplaceEqualsWithEqualityIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceEqualsWithEqualityIntentionName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new EqualsPredicate();
    }

    @Override
    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        PsiMethodCallExpression call =
            (PsiMethodCallExpression) element;
        if (call == null) {
            return;
        }
        PsiReferenceExpression methodExpression =
            call.getMethodExpression();
        PsiExpression target = methodExpression.getQualifierExpression();
        if (target == null) {
            return;
        }
        PsiExpressionList argumentList = call.getArgumentList();
        PsiExpression arg = argumentList.getExpressions()[0];
        PsiExpression strippedTarget =
            ParenthesesUtils.stripParentheses(target);
        if (strippedTarget == null) {
            return;
        }
        PsiExpression strippedArg =
            ParenthesesUtils.stripParentheses(arg);
        if (strippedArg == null) {
            return;
        }
        String strippedArgText;
        if (ParenthesesUtils.getPrecedence(strippedArg) >
            ParenthesesUtils.EQUALITY_PRECEDENCE) {
            strippedArgText = '(' + strippedArg.getText() + ')';
        }
        else {
            strippedArgText = strippedArg.getText();
        }
        String strippedTargetText;
        if (ParenthesesUtils.getPrecedence(strippedTarget) >
            ParenthesesUtils.EQUALITY_PRECEDENCE) {
            strippedTargetText = '(' + strippedTarget.getText() + ')';
        }
        else {
            strippedTargetText = strippedTarget.getText();
        }
        replaceExpression(strippedTargetText + "==" + strippedArgText, call);
    }
}