/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ipp.trivialif;

import com.intellij.java.impl.ipp.base.Intention;
import com.intellij.java.impl.ipp.base.PsiElementPredicate;
import com.intellij.java.impl.ipp.psiutils.ConditionalUtils;
import com.intellij.java.impl.ipp.psiutils.EquivalenceChecker;
import com.intellij.java.language.impl.psi.impl.PsiDiamondTypeUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.IntentionPowerPackLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
@IntentionMetaData(ignoreId = "java.ReplaceIfWithConditionalIntention", fileExtensions = "java", categories = {"Java", "Boolean"})
public class ReplaceIfWithConditionalIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return IntentionPowerPackLocalize.replaceIfWithConditionalIntentionName();
    }

    @Override
    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ReplaceIfWithConditionalPredicate();
    }

    @Override
    public void processIntention(@Nonnull PsiElement element)
        throws IncorrectOperationException {
        PsiIfStatement ifStatement = (PsiIfStatement) element.getParent();
        if (ifStatement == null) {
            return;
        }
        if (ReplaceIfWithConditionalPredicate.isReplaceableAssignment(ifStatement)) {
            PsiExpression condition = ifStatement.getCondition();
            if (condition == null) {
                return;
            }
            PsiStatement thenBranch = ifStatement.getThenBranch();
            PsiExpressionStatement strippedThenBranch = (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
            PsiStatement elseBranch = ifStatement.getElseBranch();
            PsiExpressionStatement strippedElseBranch = (PsiExpressionStatement) ConditionalUtils.stripBraces(elseBranch);
            PsiAssignmentExpression thenAssign = (PsiAssignmentExpression) strippedThenBranch.getExpression();
            PsiAssignmentExpression elseAssign = (PsiAssignmentExpression) strippedElseBranch.getExpression();
            PsiExpression lhs = thenAssign.getLExpression();
            String lhsText = lhs.getText();
            PsiJavaToken sign = thenAssign.getOperationSign();
            String operator = sign.getText();
            PsiExpression thenRhs = thenAssign.getRExpression();
            if (thenRhs == null) {
                return;
            }
            PsiExpression elseRhs = elseAssign.getRExpression();
            if (elseRhs == null) {
                return;
            }
            String conditional = getConditionalText(condition, thenRhs, elseRhs, thenAssign.getType());
            replaceStatement(lhsText + operator + conditional + ';', ifStatement);
        }
        else if (ReplaceIfWithConditionalPredicate.isReplaceableReturn(ifStatement)) {
            PsiExpression condition = ifStatement.getCondition();
            if (condition == null) {
                return;
            }
            PsiStatement thenBranch = ifStatement.getThenBranch();
            PsiReturnStatement thenReturn = (PsiReturnStatement) ConditionalUtils.stripBraces(thenBranch);
            PsiStatement elseBranch = ifStatement.getElseBranch();
            PsiReturnStatement elseReturn = (PsiReturnStatement) ConditionalUtils.stripBraces(elseBranch);
            PsiExpression thenReturnValue = thenReturn.getReturnValue();
            if (thenReturnValue == null) {
                return;
            }
            PsiExpression elseReturnValue = elseReturn.getReturnValue();
            if (elseReturnValue == null) {
                return;
            }
            PsiMethod method = PsiTreeUtil.getParentOfType(thenReturn, PsiMethod.class);
            if (method == null) {
                return;
            }
            PsiType returnType = method.getReturnType();
            String conditional = getConditionalText(condition, thenReturnValue, elseReturnValue, returnType);
            replaceStatement("return " + conditional + ';', ifStatement);
        }
        else if (ReplaceIfWithConditionalPredicate.isReplaceableMethodCall(ifStatement)) {
            PsiExpression condition = ifStatement.getCondition();
            if (condition == null) {
                return;
            }
            PsiExpressionStatement thenBranch = (PsiExpressionStatement) ConditionalUtils.stripBraces(ifStatement.getThenBranch());
            PsiExpressionStatement elseBranch = (PsiExpressionStatement) ConditionalUtils.stripBraces(ifStatement.getElseBranch());
            PsiMethodCallExpression thenMethodCallExpression = (PsiMethodCallExpression) thenBranch.getExpression();
            PsiMethodCallExpression elseMethodCallExpression = (PsiMethodCallExpression) elseBranch.getExpression();
            StringBuilder replacementText = new StringBuilder(thenMethodCallExpression.getMethodExpression().getText());
            replacementText.append('(');
            PsiExpressionList thenArgumentList = thenMethodCallExpression.getArgumentList();
            PsiExpression[] thenArguments = thenArgumentList.getExpressions();
            PsiExpressionList elseArgumentList = elseMethodCallExpression.getArgumentList();
            PsiExpression[] elseArguments = elseArgumentList.getExpressions();
            for (int i = 0, length = thenArguments.length; i < length; i++) {
                if (i > 0) {
                    replacementText.append(',');
                }
                PsiExpression thenArgument = thenArguments[i];
                PsiExpression elseArgument = elseArguments[i];
                if (EquivalenceChecker.expressionsAreEquivalent(thenArgument, elseArgument)) {
                    replacementText.append(thenArgument.getText());
                }
                else {
                    PsiMethod method = thenMethodCallExpression.resolveMethod();
                    if (method == null) {
                        return;
                    }
                    PsiParameterList parameterList = method.getParameterList();
                    PsiType requiredType = parameterList.getParameters()[i].getType();
                    String conditionalText = getConditionalText(condition, thenArgument, elseArgument, requiredType);
                    if (conditionalText == null) {
                        return;
                    }
                    replacementText.append(conditionalText);
                }
            }
            replacementText.append(");");
            replaceStatement(replacementText.toString(), ifStatement);
        }
        else if (ReplaceIfWithConditionalPredicate.isReplaceableImplicitReturn(ifStatement)) {
            PsiExpression condition = ifStatement.getCondition();
            if (condition == null) {
                return;
            }
            PsiReturnStatement thenBranch = (PsiReturnStatement) ConditionalUtils.stripBraces(ifStatement.getThenBranch());
            PsiExpression thenReturnValue = thenBranch.getReturnValue();
            if (thenReturnValue == null) {
                return;
            }
            PsiReturnStatement elseBranch = PsiTreeUtil.getNextSiblingOfType(ifStatement, PsiReturnStatement.class);
            if (elseBranch == null) {
                return;
            }
            PsiExpression elseReturnValue = elseBranch.getReturnValue();
            if (elseReturnValue == null) {
                return;
            }
            PsiMethod method = PsiTreeUtil.getParentOfType(thenBranch, PsiMethod.class);
            if (method == null) {
                return;
            }
            PsiType methodType = method.getReturnType();
            String conditional = getConditionalText(condition, thenReturnValue, elseReturnValue, methodType);
            if (conditional == null) {
                return;
            }
            replaceStatement("return " + conditional + ';', ifStatement);
            elseBranch.delete();
        }
    }

    private static String getConditionalText(PsiExpression condition,
                                             PsiExpression thenValue,
                                             PsiExpression elseValue,
                                             PsiType requiredType) {
        condition = ParenthesesUtils.stripParentheses(condition);
        thenValue = ParenthesesUtils.stripParentheses(thenValue);
        elseValue = ParenthesesUtils.stripParentheses(elseValue);

        thenValue = expandDiamondsWhenNeeded(thenValue, requiredType);
        if (thenValue == null) {
            return null;
        }
        elseValue = expandDiamondsWhenNeeded(elseValue, requiredType);
        if (elseValue == null) {
            return null;
        }
        StringBuilder conditional = new StringBuilder();
        String conditionText = getExpressionText(condition);
        conditional.append(conditionText);
        conditional.append('?');
        PsiType thenType = thenValue.getType();
        PsiType elseType = elseValue.getType();
        if (thenType instanceof PsiPrimitiveType &&
            !PsiType.NULL.equals(thenType) &&
            !(elseType instanceof PsiPrimitiveType) &&
            !(requiredType instanceof PsiPrimitiveType)) {
            // prevent unboxing of boxed value to preserve semantics (IDEADEV-36008)
            PsiPrimitiveType primitiveType = (PsiPrimitiveType) thenType;
            conditional.append(primitiveType.getBoxedTypeName());
            conditional.append(".valueOf(");
            conditional.append(thenValue.getText());
            conditional.append("):");
            conditional.append(getExpressionText(elseValue));
        }
        else if (elseType instanceof PsiPrimitiveType &&
            !PsiType.NULL.equals(elseType) &&
            !(thenType instanceof PsiPrimitiveType) &&
            !(requiredType instanceof PsiPrimitiveType)) {
            // prevent unboxing of boxed value to preserve semantics (IDEADEV-36008)
            conditional.append(getExpressionText(thenValue));
            conditional.append(':');
            PsiPrimitiveType primitiveType = (PsiPrimitiveType) elseType;
            conditional.append(primitiveType.getBoxedTypeName());
            conditional.append(".valueOf(");
            conditional.append(elseValue.getText());
            conditional.append(')');
        }
        else {
            conditional.append(getExpressionText(thenValue));
            conditional.append(':');
            conditional.append(getExpressionText(elseValue));
        }
        return conditional.toString();
    }

    private static PsiExpression expandDiamondsWhenNeeded(PsiExpression thenValue, PsiType requiredType) {
        if (thenValue instanceof PsiNewExpression) {
            if (!PsiDiamondTypeUtil.canChangeContextForDiamond((PsiNewExpression) thenValue, requiredType)) {
                return PsiDiamondTypeUtil.expandTopLevelDiamondsInside(thenValue);
            }
        }
        return thenValue;
    }

    private static String getExpressionText(PsiExpression expression) {
        if (ParenthesesUtils.getPrecedence(expression) <=
            ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
            return expression.getText();
        }
        else {
            return '(' + expression.getText() + ')';
        }
    }
}
