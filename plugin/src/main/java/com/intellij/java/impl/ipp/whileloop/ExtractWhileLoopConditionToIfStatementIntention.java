/*
 * Copyright 2007 Bas Leijdekkers
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
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ExtractWhileLoopConditionToIfStatementIntention", fileExtensions = "java", categories = {"Java", "Control Flow"})
public class ExtractWhileLoopConditionToIfStatementIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.extractWhileLoopConditionToIfStatementIntentionName();
    }

    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new WhileLoopPredicate();
    }

    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiWhileStatement whileStatement =
            (PsiWhileStatement) element.getParent();
        if (whileStatement == null) {
            return;
        }
        PsiExpression condition = whileStatement.getCondition();
        if (condition == null) {
            return;
        }
        String conditionText = condition.getText();
        PsiManager manager = whileStatement.getManager();
        PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
        PsiExpression newCondition =
            factory.createExpressionFromText("true", whileStatement);
        condition.replace(newCondition);
        PsiStatement body = whileStatement.getBody();
        String ifStatementText = "if (!(" + conditionText + ")) break;";
        PsiStatement ifStatement =
            factory.createStatementFromText(ifStatementText,
                whileStatement);
        PsiElement newElement;
        if (body instanceof PsiBlockStatement) {
            PsiBlockStatement blockStatement = (PsiBlockStatement) body;
            PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            PsiElement bodyElement = codeBlock.getFirstBodyElement();
            newElement = codeBlock.addBefore(ifStatement, bodyElement);
        }
        else if (body != null) {
            PsiBlockStatement blockStatement =
                (PsiBlockStatement) factory.createStatementFromText("{}",
                    whileStatement);
            PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            codeBlock.add(ifStatement);
            if (!(body instanceof PsiEmptyStatement)) {
                codeBlock.add(body);
            }
            newElement = body.replace(blockStatement);
        }
        else {
            return;
        }
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
        codeStyleManager.reformat(newElement);
    }
}