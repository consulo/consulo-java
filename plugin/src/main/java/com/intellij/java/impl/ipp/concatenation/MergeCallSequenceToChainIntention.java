/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.ipp.concatenation;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * @author Bas Leijdekkers
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "java.MergeCallSequenceToChainIntention", fileExtensions = "java", categories = {"Java", "Strings"})
public class MergeCallSequenceToChainIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.mergeCallSequenceToChainIntentionName();
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new CallSequencePredicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        if (!(element instanceof PsiExpressionStatement)) {
            return;
        }
        PsiExpressionStatement statement = (PsiExpressionStatement) element;
        PsiExpressionStatement nextSibling = PsiTreeUtil.getNextSiblingOfType(statement, PsiExpressionStatement.class);
        if (nextSibling == null) {
            return;
        }
        PsiExpression expression = statement.getExpression();
        StringBuilder newMethodCallExpression = new StringBuilder(expression.getText());
        PsiExpression expression1 = nextSibling.getExpression();
        if (!(expression1 instanceof PsiMethodCallExpression)) {
            return;
        }
        PsiMethodCallExpression methodCallExpression = getRootMethodCallExpression((PsiMethodCallExpression) expression1);
        while (true) {
            PsiExpressionList argumentList = methodCallExpression.getArgumentList();
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            newMethodCallExpression.append('.').append(methodName).append(argumentList.getText());
            PsiElement parent = methodCallExpression.getParent();
            PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiMethodCallExpression)) {
                break;
            }
            methodCallExpression = (PsiMethodCallExpression) grandParent;
        }
        replaceExpression(newMethodCallExpression.toString(), expression);
        nextSibling.delete();
    }

    public static PsiMethodCallExpression getRootMethodCallExpression(PsiMethodCallExpression expression) {
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        if (qualifierExpression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) qualifierExpression;
            return getRootMethodCallExpression(methodCallExpression);
        }
        return expression;
    }
}
