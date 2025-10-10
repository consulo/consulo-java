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
package com.intellij.java.impl.ipp.commutative;

import com.intellij.java.impl.ipp.base.MutablyNamedIntention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.FlipCommutativeMethodCallIntention", fileExtensions = "java", categories = {"Java", "Other"})
public class FlipCommutativeMethodCallIntention extends MutablyNamedIntention {

    @Override
    protected LocalizeValue getTextForElement(PsiElement element) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression =
            call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        assert methodName != null;
        if ("equals".equals(methodName) ||
            "equalsIgnoreCase".equals(methodName)) {
            return IntentionPowerPackLocalize.flipCommutativeMethodCallIntentionName(methodName);
        }
        else {
            return IntentionPowerPackLocalize.flipCommutativeMethodCallIntentionName1(methodName);
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getNeutralText() {
        return IntentionPowerPackLocalize.flipCommutativeMethodCallIntentionFamilyName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new FlipCommutativeMethodCallPredicate();
    }

    @Override
    public void processIntention(PsiElement element)
        throws IncorrectOperationException {
        final PsiMethodCallExpression call =
            (PsiMethodCallExpression) element;
        final PsiReferenceExpression methodExpression =
            call.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        final PsiExpression target = methodExpression.getQualifierExpression();
        if (target == null) {
            return;
        }
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression arg = argumentList.getExpressions()[0];
        final PsiExpression strippedTarget =
            ParenthesesUtils.stripParentheses(target);
        if (strippedTarget == null) {
            return;
        }
        final PsiExpression strippedArg =
            ParenthesesUtils.stripParentheses(arg);
        if (strippedArg == null) {
            return;
        }
        final String callString;
        if (ParenthesesUtils.getPrecedence(strippedArg) >
            ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
            callString = '(' + strippedArg.getText() + ")." + methodName + '(' +
                strippedTarget.getText() + ')';
        }
        else {
            callString = strippedArg.getText() + '.' + methodName + '(' +
                strippedTarget.getText() + ')';
        }
        replaceExpression(callString, call);
    }
}