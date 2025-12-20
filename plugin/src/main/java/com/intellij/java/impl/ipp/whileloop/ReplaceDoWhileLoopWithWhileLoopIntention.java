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
@IntentionMetaData(ignoreId = "java.ReplaceDoWhileLoopWithWhileLoopIntention", fileExtensions = "java", categories = {"Java", "Control Flow"})
public class ReplaceDoWhileLoopWithWhileLoopIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceDoWhileLoopWithWhileLoopIntentionName();
    }

    @Nonnull
    protected PsiElementPredicate getElementPredicate() {
        return new DoWhileLoopPredicate();
    }

    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiDoWhileStatement doWhileStatement =
            (PsiDoWhileStatement) element.getParent();
        if (doWhileStatement == null) {
            return;
        }
        PsiStatement body = doWhileStatement.getBody();
        PsiElement parent = doWhileStatement.getParent();
        StringBuilder whileStatementText = new StringBuilder("while(");
        PsiExpression condition = doWhileStatement.getCondition();
        if (condition != null) {
            whileStatementText.append(condition.getText());
        }
        whileStatementText.append(')');
        if (body instanceof PsiBlockStatement) {
            whileStatementText.append('{');
            PsiBlockStatement blockStatement = (PsiBlockStatement) body;
            PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
            PsiElement[] children = codeBlock.getChildren();
            if (children.length > 2) {
                for (int i = 1; i < children.length - 1; i++) {
                    PsiElement child = children[i];
                    parent.addBefore(child, doWhileStatement);
                    if (child instanceof PsiDeclarationStatement) {
                        PsiDeclarationStatement declarationStatement =
                            (PsiDeclarationStatement) child;
                        PsiElement[] declaredElements =
                            declarationStatement.getDeclaredElements();
                        for (PsiElement declaredElement : declaredElements) {
                            if (declaredElement instanceof PsiVariable) {
                                // prevent duplicate variable declarations.
                                PsiVariable variable =
                                    (PsiVariable) declaredElement;
                                PsiExpression initializer =
                                    variable.getInitializer();
                                if (initializer != null) {
                                    String name = variable.getName();
                                    whileStatementText.append(name);
                                    whileStatementText.append(" = ");
                                    whileStatementText.append(
                                        initializer.getText());
                                    whileStatementText.append(';');
                                }
                            }
                        }
                    }
                    else {
                        whileStatementText.append(child.getText());
                    }
                }
            }
            whileStatementText.append('}');
        }
        else if (body != null) {
            parent.addBefore(body, doWhileStatement);
            whileStatementText.append(body.getText());
        }
        replaceStatement(whileStatementText.toString(), doWhileStatement);
    }
}
