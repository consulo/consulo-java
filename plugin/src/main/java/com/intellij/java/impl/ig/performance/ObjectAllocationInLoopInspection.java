/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.performance;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ObjectAllocationInLoopInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.objectAllocationInLoopDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.objectAllocationInLoopProblemDescriptor().get();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObjectAllocationInLoopsVisitor();
    }

    private static class ObjectAllocationInLoopsVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitNewExpression(@Nonnull PsiNewExpression expression) {
            super.visitNewExpression(expression);
            if (!ControlFlowUtils.isInLoop(expression)) {
                return;
            }
            if (ControlFlowUtils.isInExitStatement(expression)) {
                return;
            }
            PsiStatement newExpressionStatement = PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
            if (newExpressionStatement == null) {
                return;
            }
            PsiStatement parentStatement = PsiTreeUtil.getParentOfType(newExpressionStatement, PsiStatement.class);
            if (!ControlFlowUtils.statementMayCompleteNormally(parentStatement)) {
                return;
            }
            if (isAllocatedOnlyOnce(expression)) {
                return;
            }
            registerError(expression);
        }

        private static boolean isAllocatedOnlyOnce(PsiNewExpression expression) {
            PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiAssignmentExpression)) {
                return false;
            }
            PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) parent;
            PsiExpression lExpression = assignmentExpression.getLExpression();
            if (!(lExpression instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(assignmentExpression, PsiIfStatement.class);
            if (ifStatement == null) {
                return false;
            }
            PsiExpression condition = ifStatement.getCondition();
            if (!(condition instanceof PsiBinaryExpression)) {
                return false;
            }
            PsiBinaryExpression binaryExpression = (PsiBinaryExpression) condition;
            if (binaryExpression.getOperationTokenType() != JavaTokenType.EQEQ) {
                return false;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) lExpression;
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            if (lhs instanceof PsiLiteralExpression) {
                if (!"null".equals(lhs.getText())) {
                    return false;
                }
                if (!(rhs instanceof PsiReferenceExpression)) {
                    return false;
                }
                return referenceExpression.getText().equals(rhs.getText());
            }
            else if (rhs instanceof PsiLiteralExpression) {
                if (!"null".equals(rhs.getText())) {
                    return false;
                }
                if (!(lhs instanceof PsiReferenceExpression)) {
                    return false;
                }
                return referenceExpression.getText().equals(lhs.getText());
            }
            else {
                return false;
            }
        }
    }
}