/*
 * Copyright 2008-2011 Bas Leijdekkers
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
package com.intellij.java.impl.ig.bugs;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class VariableNotUsedInsideIfInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.variableNotUsedInsideIfDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.variableNotUsedInsideIfProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new VariableNotUsedInsideIfVisitor();
    }

    private static class VariableNotUsedInsideIfVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitConditionalExpression(PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            final PsiExpression condition = expression.getCondition();
            if (!(condition instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) condition;
            final PsiReferenceExpression referenceExpression = extractVariableReference(binaryExpression);
            if (referenceExpression == null) {
                return;
            }
            final IElementType tokenType = binaryExpression.getOperationTokenType();
            if (tokenType == JavaTokenType.EQEQ) {
                checkVariableUsage(referenceExpression, expression.getThenExpression(), expression.getElseExpression());
            }
            else if (tokenType == JavaTokenType.NE) {
                checkVariableUsage(referenceExpression, expression.getElseExpression(), expression.getThenExpression());
            }
        }

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            final PsiExpression condition = statement.getCondition();
            if (!(condition instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) condition;
            final PsiReferenceExpression referenceExpression = extractVariableReference(binaryExpression);
            if (referenceExpression == null) {
                return;
            }
            final IElementType tokenType = binaryExpression.getOperationTokenType();
            if (tokenType == JavaTokenType.EQEQ) {
                checkVariableUsage(referenceExpression, statement.getThenBranch(), statement.getElseBranch());
            }
            else if (tokenType == JavaTokenType.NE) {
                checkVariableUsage(referenceExpression, statement.getElseBranch(), statement.getThenBranch());
            }
        }

        private void checkVariableUsage(
            PsiReferenceExpression referenceExpression,
            PsiElement thenContext, PsiElement elseContext
        ) {
            if (thenContext == null) {
                return;
            }
            final PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return;
            }
            final PsiVariable variable = (PsiVariable) target;
            if (contextExits(thenContext) || VariableAccessUtils.variableIsAssigned(variable, thenContext)) {
                return;
            }
            if (elseContext != null &&
                (contextExits(elseContext) || VariableAccessUtils.variableIsUsed(variable, elseContext))) {
                return;
            }
            registerError(referenceExpression);
        }

        private static PsiReferenceExpression extractVariableReference(PsiBinaryExpression expression) {
            final PsiExpression lhs = ParenthesesUtils.stripParentheses(expression.getLOperand());
            if (lhs == null) {
                return null;
            }
            final PsiExpression rhs = ParenthesesUtils.stripParentheses(expression.getROperand());
            if (rhs == null) {
                return null;
            }
            if (PsiType.NULL.equals(rhs.getType())) {
                if (!(lhs instanceof PsiReferenceExpression)) {
                    return null;
                }
                return (PsiReferenceExpression) lhs;
            }
            if (PsiType.NULL.equals(lhs.getType())) {
                if (!(rhs instanceof PsiReferenceExpression)) {
                    return null;
                }
                return (PsiReferenceExpression) rhs;
            }
            return null;
        }

        private static boolean contextExits(PsiElement context) {
            if (context instanceof PsiBlockStatement) {
                final PsiBlockStatement blockStatement = (PsiBlockStatement) context;
                final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
                final PsiStatement[] statements = codeBlock.getStatements();
                if (statements.length == 0) {
                    return false;
                }
                final PsiStatement lastStatement = statements[statements.length - 1];
                return statementExits(lastStatement);
            }
            else {
                return statementExits(context);
            }
        }

        private static boolean statementExits(PsiElement context) {
            return context instanceof PsiReturnStatement ||
                context instanceof PsiThrowStatement ||
                context instanceof PsiBreakStatement ||
                context instanceof PsiContinueStatement;
        }
    }
}
