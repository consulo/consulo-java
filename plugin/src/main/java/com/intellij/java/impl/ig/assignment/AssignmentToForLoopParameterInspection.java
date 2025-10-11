/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.assignment;

import com.intellij.java.impl.ig.fixes.ExtractParameterAsLocalVariableFix;
import com.intellij.java.impl.ig.psiutils.WellFormednessUtils;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class AssignmentToForLoopParameterInspection extends BaseInspection {
    /**
     * @noinspection PublicField for externalization purposes
     */
    public boolean m_checkForeachParameters = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.assignmentToForLoopParameterDisplayName();
    }

    @Nonnull
    @Override
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.assignmentToForLoopParameterProblemDescriptor().get();
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.assignmentToForLoopParameterCheckForeachOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "m_checkForeachParameters");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        final Boolean foreachLoop = (Boolean) infos[0];
        if (!foreachLoop.booleanValue()) {
            return null;
        }
        return new ExtractParameterAsLocalVariableFix();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new AssignmentToForLoopParameterVisitor();
    }

    private class AssignmentToForLoopParameterVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitAssignmentExpression(
            @Nonnull PsiAssignmentExpression expression
        ) {
            super.visitAssignmentExpression(expression);
            if (!WellFormednessUtils.isWellFormed(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            checkForForLoopParam(lhs);
            checkForForeachLoopParam(lhs);
        }

        @Override
        public void visitPrefixExpression(
            @Nonnull PsiPrefixExpression expression
        ) {
            super.visitPrefixExpression(expression);
            final IElementType tokenType = expression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            checkForForLoopParam(operand);
            checkForForeachLoopParam(operand);  //sensible due to autoboxing/unboxing
        }

        @Override
        public void visitPostfixExpression(
            @Nonnull PsiPostfixExpression expression
        ) {
            super.visitPostfixExpression(expression);
            final IElementType tokenType = expression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            checkForForLoopParam(operand);
            checkForForeachLoopParam(operand);  //sensible due to autoboxing/unboxing
        }

        private void checkForForLoopParam(PsiExpression expression) {
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) expression;
            final PsiElement element = referenceExpression.resolve();
            if (!(element instanceof PsiLocalVariable)) {
                return;
            }
            final PsiLocalVariable variable = (PsiLocalVariable) element;
            final PsiElement variableParent = variable.getParent();
            if (!(variableParent instanceof PsiDeclarationStatement)) {
                return;
            }
            final PsiDeclarationStatement declarationStatement =
                (PsiDeclarationStatement) variableParent;
            final PsiElement parent = declarationStatement.getParent();
            if (!(parent instanceof PsiForStatement)) {
                return;
            }
            final PsiForStatement forStatement = (PsiForStatement) parent;
            final PsiStatement initialization =
                forStatement.getInitialization();
            if (initialization == null) {
                return;
            }
            if (!initialization.equals(declarationStatement)) {
                return;
            }
            if (!isInForStatementBody(expression, forStatement)) {
                return;
            }
            registerError(expression, Boolean.FALSE);
        }

        private void checkForForeachLoopParam(PsiExpression expression) {
            if (!m_checkForeachParameters) {
                return;
            }
            if (!(expression instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) expression;
            final PsiElement element = referenceExpression.resolve();
            if (!(element instanceof PsiParameter)) {
                return;
            }
            final PsiParameter parameter = (PsiParameter) element;
            if (!(parameter.getParent() instanceof PsiForeachStatement)) {
                return;
            }
            registerError(expression, Boolean.TRUE);
        }

        private boolean isInForStatementBody(
            PsiExpression expression,
            PsiForStatement statement
        ) {
            final PsiStatement body = statement.getBody();
            if (body == null) {
                return false;
            }
            return PsiTreeUtil.isAncestor(body, expression, true);
        }
    }
}