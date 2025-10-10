/*
 * Copyright 2006-2007 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.whileloop;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.language.psi.*;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceWhileLoopWithDoWhileLoopIntention", fileExtensions = "java", categories = {"Java", "Control Flow"})
public class ReplaceWhileLoopWithDoWhileLoopIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceWhileLoopWithDoWhileLoopIntentionName();
    }

    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new WhileLoopPredicate();
    }

    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        final PsiWhileStatement whileStatement =
            (PsiWhileStatement) element.getParent();
        if (whileStatement == null) {
            return;
        }
        final PsiStatement body = whileStatement.getBody();
        final StringBuilder doWhileStatementText = new StringBuilder("if(");
        final PsiExpression condition = whileStatement.getCondition();
        if (condition != null) {
            doWhileStatementText.append(condition.getText());
        }
        doWhileStatementText.append(") {\n");
        if (body instanceof PsiBlockStatement) {
            doWhileStatementText.append("do {");
            final PsiBlockStatement blockStatement = (PsiBlockStatement) body;
            final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            final PsiElement[] children = codeBlock.getChildren();
            if (children.length > 2) {
                for (int i = 1; i < children.length - 1; i++) {
                    final PsiElement child = children[i];
                    doWhileStatementText.append(child.getText());
                }
            }
            doWhileStatementText.append('}');
        }
        else if (body != null) {
            doWhileStatementText.append(body.getText());
        }
        doWhileStatementText.append("while(");
        if (condition != null) {
            doWhileStatementText.append(condition.getText());
        }
        doWhileStatementText.append(");\n}");
        replaceStatement(doWhileStatementText.toString(), whileStatement);
    }
}
