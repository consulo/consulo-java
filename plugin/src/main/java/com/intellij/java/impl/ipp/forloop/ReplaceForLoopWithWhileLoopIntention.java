/*
 * Copyright 2006-2010 Bas Leijdekkers
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
package com.intellij.java.impl.ipp.forloop;

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
@IntentionMetaData(ignoreId = "java.ReplaceForLoopWithWhileLoopIntention", fileExtensions = "java", categories = {"Java", "Control Flow"})
public class ReplaceForLoopWithWhileLoopIntention extends Intention {

    @Override
    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new ForLoopPredicate();
    }

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceForLoopWithWhileLoopIntentionName();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiForStatement forStatement =
            (PsiForStatement) element.getParent();
        if (forStatement == null) {
            return;
        }
        PsiStatement initialization = forStatement.getInitialization();
        if (initialization != null &&
            !(initialization instanceof PsiEmptyStatement)) {
            PsiElement parent = forStatement.getParent();
            parent.addBefore(initialization, forStatement);
        }
        JavaPsiFacade psiFacade =
            JavaPsiFacade.getInstance(element.getProject());
        PsiElementFactory factory = psiFacade.getElementFactory();
        PsiWhileStatement whileStatement =
            (PsiWhileStatement) factory.createStatementFromText(
                "while(true) {}", element);
        PsiExpression forCondition = forStatement.getCondition();
        PsiExpression whileCondition = whileStatement.getCondition();
        PsiStatement body = forStatement.getBody();
        if (forCondition != null) {
            assert whileCondition != null;
            whileCondition.replace(forCondition);
        }
        PsiBlockStatement blockStatement =
            (PsiBlockStatement) whileStatement.getBody();
        if (blockStatement == null) {
            return;
        }
        PsiElement newBody;
        if (body instanceof PsiBlockStatement) {
            PsiBlockStatement newWhileBody =
                (PsiBlockStatement) blockStatement.replace(body);
            newBody = newWhileBody.getCodeBlock();
        }
        else {
            PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            if (body != null && !(body instanceof PsiEmptyStatement)) {
                codeBlock.addAfter(body, codeBlock.getFirstChild());
            }
            newBody = codeBlock;
        }
        PsiStatement update = forStatement.getUpdate();
        if (update != null) {
            PsiStatement[] updateStatements;
            if (update instanceof PsiExpressionListStatement) {
                PsiExpressionListStatement expressionListStatement =
                    (PsiExpressionListStatement) update;
                PsiExpressionList expressionList =
                    expressionListStatement.getExpressionList();
                PsiExpression[] expressions =
                    expressionList.getExpressions();
                updateStatements = new PsiStatement[expressions.length];
                for (int i = 0, expressionsLength = expressions.length;
                     i < expressionsLength; i++) {
                    PsiExpression expression = expressions[i];
                    PsiStatement updateStatement =
                        factory.createStatementFromText(
                            expression.getText() + ';', element);
                    updateStatements[i] = updateStatement;
                }
            }
            else {
                PsiStatement updateStatement =
                    factory.createStatementFromText(
                        update.getText() + ';', element);
                updateStatements = new PsiStatement[]{updateStatement};
            }
            newBody.accept(new UpdateInserter(whileStatement, updateStatements));
            for (PsiStatement updateStatement : updateStatements) {
                newBody.addBefore(updateStatement, newBody.getLastChild());
            }
        }
        forStatement.replace(whileStatement);
    }

    private static class UpdateInserter extends JavaRecursiveElementWalkingVisitor {

        private final PsiWhileStatement whileStatement;
        private final PsiStatement[] updateStatements;

        private UpdateInserter(PsiWhileStatement whileStatement,
                               PsiStatement[] updateStatements) {
            this.whileStatement = whileStatement;
            this.updateStatements = updateStatements;
        }

        @Override
        public void visitContinueStatement(PsiContinueStatement statement) {
            PsiStatement continuedStatement =
                statement.findContinuedStatement();
            if (!whileStatement.equals(continuedStatement)) {
                return;
            }
            PsiElement parent = statement.getParent();
            if (parent == null) {
                return;
            }
            for (PsiStatement updateStatement : updateStatements) {
                parent.addBefore(updateStatement, statement);
            }
            super.visitContinueStatement(statement);
        }
    }
}