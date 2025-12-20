/*
 * Copyright 2010 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.asserttoif;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.AssertToIfIntention", fileExtensions = "java", categories = {
    "Java",
    "Other"
})
public class IfToAssertionIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.ifToAssertionIntentionName();
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new IfStatementPredicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiElement parent = element.getParent();
        if (!(parent instanceof PsiIfStatement)) {
            return;
        }
        PsiIfStatement ifStatement = (PsiIfStatement) parent;

        PsiExpression condition = ifStatement.getCondition();
        String negatedExpressionText =
            BoolUtils.getNegatedExpressionText(condition);
        StringBuilder newStatementText = new StringBuilder("assert ");
        newStatementText.append(negatedExpressionText);
        PsiStatement thenBranch = ifStatement.getThenBranch();
        String message = getMessage(thenBranch);
        if (message != null) {
            newStatementText.append(':');
            newStatementText.append(message);
        }
        newStatementText.append(';');
        replaceStatement(newStatementText.toString(), ifStatement);
    }

    private static String getMessage(PsiElement element) {
        if (element instanceof PsiBlockStatement) {
            PsiBlockStatement blockStatement = (PsiBlockStatement) element;
            PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            PsiStatement[] statements = codeBlock.getStatements();
            if (statements.length != 1) {
                return null;
            }
            PsiStatement statement = statements[0];
            return getMessage(statement);
        }
        else if (element instanceof PsiThrowStatement) {
            PsiThrowStatement throwStatement = (PsiThrowStatement) element;

            PsiExpression exception = throwStatement.getException();
            if (!(exception instanceof PsiNewExpression)) {
                return null;
            }
            PsiNewExpression newExpression = (PsiNewExpression) exception;
            PsiExpressionList argumentList =
                newExpression.getArgumentList();
            if (argumentList == null) {
                return null;
            }
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return null;
            }
            PsiExpression argument = arguments[0];
            return argument.getText();
        }
        return null;
    }
}
