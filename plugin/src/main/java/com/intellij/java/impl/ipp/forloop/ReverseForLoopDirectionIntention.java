/*
 * Copyright 2009-2010 Bas Leijdekkers
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
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReverseForLoopDirectionIntention", fileExtensions = "java", categories = {"Java", "Control Flow"})
public class ReverseForLoopDirectionIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.reverseForLoopDirectionIntentionName();
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ReverseForLoopDirectionPredicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiForStatement forStatement =
            (PsiForStatement) element.getParent();
        PsiDeclarationStatement initialization =
            (PsiDeclarationStatement) forStatement.getInitialization();
        if (initialization == null) {
            return;
        }
        PsiBinaryExpression condition =
            (PsiBinaryExpression) forStatement.getCondition();
        if (condition == null) {
            return;
        }
        PsiLocalVariable variable =
            (PsiLocalVariable) initialization.getDeclaredElements()[0];
        PsiExpression initializer = variable.getInitializer();
        if (initializer == null) {
            return;
        }
        PsiExpression lhs = condition.getLOperand();
        PsiExpression rhs = condition.getROperand();
        if (rhs == null) {
            return;
        }
        PsiExpressionStatement update =
            (PsiExpressionStatement) forStatement.getUpdate();
        if (update == null) {
            return;
        }
        PsiExpression updateExpression = update.getExpression();
        String variableName = variable.getName();
        StringBuilder newUpdateText = new StringBuilder();
        if (updateExpression instanceof PsiPrefixExpression) {
            PsiPrefixExpression prefixExpression =
                (PsiPrefixExpression) updateExpression;
            IElementType tokenType =
                prefixExpression.getOperationTokenType();
            if (JavaTokenType.PLUSPLUS == tokenType) {
                newUpdateText.append("--");
            }
            else if (JavaTokenType.MINUSMINUS == tokenType) {
                newUpdateText.append("++");
            }
            else {
                return;
            }
            newUpdateText.append(variableName);
        }
        else if (updateExpression instanceof PsiPostfixExpression) {
            newUpdateText.append(variableName);
            PsiPostfixExpression postfixExpression =
                (PsiPostfixExpression) updateExpression;
            IElementType tokenType =
                postfixExpression.getOperationTokenType();
            if (JavaTokenType.PLUSPLUS == tokenType) {
                newUpdateText.append("--");
            }
            else if (JavaTokenType.MINUSMINUS == tokenType) {
                newUpdateText.append("++");
            }
            else {
                return;
            }
        }
        else {
            return;
        }
        Project project = element.getProject();
        PsiElementFactory factory =
            JavaPsiFacade.getElementFactory(project);
        PsiExpression newUpdate = factory.createExpressionFromText(
            newUpdateText.toString(), element);
        updateExpression.replace(newUpdate);
        IElementType sign = condition.getOperationTokenType();
        String negatedSign = ComparisonUtils.getNegatedComparison(sign);
        StringBuilder conditionText = new StringBuilder();
        StringBuilder newInitializerText = new StringBuilder();
        if (VariableAccessUtils.evaluatesToVariable(lhs, variable)) {
            conditionText.append(variableName);
            conditionText.append(negatedSign);
            if (sign == JavaTokenType.GE) {
                conditionText.append(incrementExpression(initializer, true));
            }
            else if (sign == JavaTokenType.LE) {
                conditionText.append(incrementExpression(initializer, false));
            }
            else {
                conditionText.append(initializer.getText());
            }
            if (sign == JavaTokenType.LT) {
                newInitializerText.append(incrementExpression(rhs, false));
            }
            else if (sign == JavaTokenType.GT) {
                newInitializerText.append(incrementExpression(rhs, true));
            }
            else {
                newInitializerText.append(rhs.getText());
            }
        }
        else if (VariableAccessUtils.evaluatesToVariable(rhs, variable)) {
            if (sign == JavaTokenType.LE) {
                conditionText.append(incrementExpression(initializer, true));
            }
            else if (sign == JavaTokenType.GE) {
                conditionText.append(incrementExpression(initializer, false));
            }
            else {
                conditionText.append(initializer.getText());
            }
            conditionText.append(negatedSign);
            conditionText.append(variableName);
            if (sign == JavaTokenType.GT) {
                newInitializerText.append(incrementExpression(lhs, false));
            }
            else if (sign == JavaTokenType.LT) {
                newInitializerText.append(incrementExpression(lhs, true));
            }
            else {
                newInitializerText.append(lhs.getText());
            }
        }
        else {
            return;
        }
        PsiExpression newInitializer = factory.createExpressionFromText(
            newInitializerText.toString(), element);
        variable.setInitializer(newInitializer);
        PsiExpression newCondition = factory.createExpressionFromText(
            conditionText.toString(), element);
        condition.replace(newCondition);
    }

    private static String incrementExpression(PsiExpression expression,
                                              boolean positive) {
        if (expression instanceof PsiLiteralExpression) {
            PsiLiteralExpression literalExpression =
                (PsiLiteralExpression) expression;
            Number value = (Number) literalExpression.getValue();
            if (value == null) {
                return null;
            }
            if (positive) {
                return String.valueOf(value.longValue() + 1L);
            }
            else {
                return String.valueOf(value.longValue() - 1L);
            }
        }
        else {
            if (expression instanceof PsiBinaryExpression) {
                // see if we can remove a -1 instead of adding a +1
                PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) expression;
                PsiExpression rhs = binaryExpression.getROperand();
                if (ExpressionUtils.isOne(rhs)) {
                    IElementType tokenType =
                        binaryExpression.getOperationTokenType();
                    if (tokenType == JavaTokenType.MINUS && positive) {
                        return binaryExpression.getLOperand().getText();
                    }
                    else if (tokenType == JavaTokenType.PLUS && !positive) {
                        return binaryExpression.getLOperand().getText();
                    }
                }
            }
            String expressionText;
            if (ParenthesesUtils.getPrecedence(expression) >
                ParenthesesUtils.ADDITIVE_PRECEDENCE) {
                expressionText = '(' + expression.getText() + ')';
            }
            else {
                expressionText = expression.getText();
            }
            if (positive) {
                return expressionText + "+1";
            }
            else {
                return expressionText + "-1";
            }
        }
    }
}