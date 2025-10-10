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
import com.siyeh.ig.psiutils.ParenthesesUtils;
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
        final PsiConditionalExpression expression = (PsiConditionalExpression) element;
        replaceConditionalWithIf(expression);
    }

    private static void replaceConditionalWithIf(PsiConditionalExpression expression) throws IncorrectOperationException {
        final PsiStatement statement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
        if (statement == null) {
            return;
        }
        final PsiVariable variable;
        if (statement instanceof PsiDeclarationStatement) {
            variable = PsiTreeUtil.getParentOfType(expression, PsiVariable.class);
        }
        else {
            variable = null;
        }
        PsiExpression thenExpression = expression.getThenExpression();
        PsiExpression elseExpression = expression.getElseExpression();
        final PsiExpression condition = expression.getCondition();
        final PsiExpression strippedCondition = ParenthesesUtils.stripParentheses(condition);
        final StringBuilder newStatement = new StringBuilder();
        newStatement.append("if(");
        if (strippedCondition != null) {
            newStatement.append(strippedCondition.getText());
        }
        newStatement.append(')');
        if (variable != null) {
            final String name = variable.getName();
            newStatement.append(name);
            newStatement.append('=');
            PsiExpression initializer = variable.getInitializer();
            if (initializer == null) {
                return;
            }
            if (initializer instanceof PsiArrayInitializerExpression) {
                final int conditionIdx = ArrayUtil.find(((PsiArrayInitializerExpression) initializer).getInitializers(), expression);
                if (conditionIdx >= 0) {
                    initializer = (PsiExpression) initializer.replace(RefactoringUtil.convertInitializerToNormalExpression(initializer,
                        variable.getType()));
                    final PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression) initializer).getArrayInitializer();
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
            final PsiManager manager = statement.getManager();
            final Project project = manager.getProject();
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            final PsiElementFactory factory = facade.getElementFactory();
            final PsiStatement ifStatement = factory.createStatementFromText(newStatement.toString(), statement);
            final PsiElement parent = statement.getParent();
            final PsiElement addedElement = parent.addAfter(ifStatement, statement);
            final CodeStyleManager styleManager = CodeStyleManager.getInstance(manager.getProject());
            styleManager.reformat(addedElement);
        }
        else {
            final boolean addBraces = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class, true, PsiStatement.class) != null;
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
        final PsiElement expressionParent = elementToReplace.getParent();
        if (expressionParent instanceof PsiParenthesizedExpression) {
            final PsiElement grandParent = expressionParent.getParent();
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
            final String replacementText = (replacementExpression == null) ? "" : replacementExpression.getText();
            out.append(replacementText);
            return;
        }
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
            out.append(element.getText());
            if (element instanceof PsiComment) {
                final PsiComment comment = (PsiComment) element;
                final IElementType tokenType = comment.getTokenType();
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