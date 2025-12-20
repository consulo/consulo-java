/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.conditional;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.PsiReplacementUtil;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceConditionalWithIfIntention", fileExtensions = "java", categories = {"Java", "Conditional Operator"})
public class ReplaceConditionalWithIfIntention extends Intention {
    private static final Logger LOG = Logger.getInstance(ReplaceConditionalWithIfIntention.class);

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceConditionalWithIfIntentionName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ReplaceConditionalWithIfPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element) throws IncorrectOperationException {
        PsiConditionalExpression expression = (PsiConditionalExpression) element;
        replaceConditionalWithIf(expression);
    }

    private static void replaceConditionalWithIf(PsiConditionalExpression expression) throws IncorrectOperationException {
        PsiStatement statement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
        if (statement == null) {
            return;
        }
        PsiVariable variable;
        if (statement instanceof PsiDeclarationStatement) {
            variable = PsiTreeUtil.getParentOfType(expression, PsiVariable.class);
        }
        else {
            variable = null;
        }
        PsiExpression thenExpression = expression.getThenExpression();
        PsiExpression elseExpression = expression.getElseExpression();
        PsiExpression condition = expression.getCondition();
        PsiExpression strippedCondition = ParenthesesUtils.stripParentheses(condition);
        StringBuilder newStatement = new StringBuilder();
        newStatement.append("if(");
        if (strippedCondition != null) {
            newStatement.append(strippedCondition.getText());
        }
        newStatement.append(')');
        if (variable != null) {
            String name = variable.getName();
            newStatement.append(name);
            newStatement.append('=');
            PsiExpression initializer = variable.getInitializer();
            if (initializer == null) {
                return;
            }
            if (initializer instanceof PsiArrayInitializerExpression) {
                int conditionIdx = ArrayUtil.find(((PsiArrayInitializerExpression) initializer).getInitializers(), expression);
                if (conditionIdx >= 0) {
                    initializer = (PsiExpression) initializer.replace(RefactoringUtil.convertInitializerToNormalExpression(initializer,
                        variable.getType()));
                    PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression) initializer).getArrayInitializer();
                    LOG.assertTrue(arrayInitializer != null, initializer.getText());
                    expression = (PsiConditionalExpression) arrayInitializer.getInitializers()[conditionIdx];
                    thenExpression = expression.getThenExpression();
                    elseExpression = expression.getElseExpression();
                }
            }
            appendElementTextWithoutParentheses(initializer, expression, thenExpression, newStatement);
            newStatement.append("; else ");
            newStatement.append(name);
            newStatement.append('=');
            appendElementTextWithoutParentheses(initializer, expression, elseExpression, newStatement);
            newStatement.append(';');
            initializer.delete();
            PsiManager manager = statement.getManager();
            Project project = manager.getProject();
            JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            PsiElementFactory factory = facade.getElementFactory();
            PsiStatement ifStatement = factory.createStatementFromText(newStatement.toString(), statement);
            PsiElement parent = statement.getParent();
            PsiElement addedElement = parent.addAfter(ifStatement, statement);
            CodeStyleManager styleManager = CodeStyleManager.getInstance(manager.getProject());
            styleManager.reformat(addedElement);
        }
        else {
            boolean addBraces = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class, true, PsiStatement.class) != null;
            if (addBraces || thenExpression == null) {
                newStatement.append('{');
            }
            appendElementTextWithoutParentheses(statement, expression, thenExpression, newStatement);
            if (addBraces) {
                newStatement.append("} else {");
            }
            else {
                if (thenExpression == null) {
                    newStatement.append('}');
                }
                newStatement.append(" else ");
                if (elseExpression == null) {
                    newStatement.append('{');
                }
            }
            appendElementTextWithoutParentheses(statement, expression, elseExpression, newStatement);
            if (addBraces || elseExpression == null) {
                newStatement.append('}');
            }
            PsiReplacementUtil.replaceStatement(statement, newStatement.toString());
        }
    }

    private static void appendElementTextWithoutParentheses(@Nonnull PsiElement element, @Nonnull PsiElement elementToReplace,
                                                            @Nullable PsiExpression replacementExpression, @Nonnull StringBuilder out) {
        PsiElement expressionParent = elementToReplace.getParent();
        if (expressionParent instanceof PsiParenthesizedExpression) {
            PsiElement grandParent = expressionParent.getParent();
            if (replacementExpression == null || !(grandParent instanceof PsiExpression) ||
                !ParenthesesUtils.areParenthesesNeeded(replacementExpression, (PsiExpression) grandParent, false)) {
                appendElementText(element, expressionParent, replacementExpression, out);
                return;
            }
        }
        appendElementText(element, elementToReplace, replacementExpression, out);
    }

    private static void appendElementText(@Nonnull PsiElement element, @Nonnull PsiElement elementToReplace,
                                          @Nullable PsiExpression replacementExpression, @Nonnull StringBuilder out) {
        if (element.equals(elementToReplace)) {
            String replacementText = (replacementExpression == null) ? "" : replacementExpression.getText();
            out.append(replacementText);
            return;
        }
        PsiElement[] children = element.getChildren();
        if (children.length == 0) {
            out.append(element.getText());
            if (element instanceof PsiComment) {
                PsiComment comment = (PsiComment) element;
                IElementType tokenType = comment.getTokenType();
                if (tokenType == JavaTokenType.END_OF_LINE_COMMENT) {
                    out.append('\n');
                }
            }
            return;
        }
        for (PsiElement child : children) {
            appendElementText(child, elementToReplace, replacementExpression, out);
        }
    }
}