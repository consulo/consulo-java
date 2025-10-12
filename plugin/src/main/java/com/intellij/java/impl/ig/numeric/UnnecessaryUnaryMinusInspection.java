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
package com.intellij.java.impl.ig.numeric;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class UnnecessaryUnaryMinusInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessaryUnaryMinusDisplayName();
    }

    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.unnecessaryUnaryMinusProblemDescriptor().get();
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryUnaryMinusFix();
    }

    private static class UnnecessaryUnaryMinusFix extends InspectionGadgetsFix {
        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.unnecessaryUnaryMinusQuickfix();
        }

        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiPrefixExpression prefixExpression = (PsiPrefixExpression) element.getParent();
            final PsiExpression parentExpression = (PsiExpression) prefixExpression.getParent();
            @NonNls final StringBuilder newExpression = new StringBuilder();
            if (parentExpression instanceof PsiAssignmentExpression) {
                final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) parentExpression;
                final PsiExpression lhs = assignmentExpression.getLExpression();
                newExpression.append(lhs.getText());
                final IElementType tokenType = assignmentExpression.getOperationTokenType();
                if (tokenType.equals(JavaTokenType.PLUSEQ)) {
                    newExpression.append("-=");
                }
                else {
                    newExpression.append("+=");
                }
            }
            else if (parentExpression instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) parentExpression;
                final PsiExpression lhs = binaryExpression.getLOperand();
                newExpression.append(lhs.getText());
                final IElementType tokenType = binaryExpression.getOperationTokenType();
                if (tokenType.equals(JavaTokenType.PLUS)) {
                    newExpression.append('-');
                }
                else {
                    newExpression.append('+');
                }
            }
            final PsiExpression operand = prefixExpression.getOperand();
            if (operand == null) {
                return;
            }
            newExpression.append(operand.getText());
            replaceExpression(parentExpression, newExpression.toString());
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryUnaryMinusVisitor();
    }

    private static class UnnecessaryUnaryMinusVisitor extends BaseInspectionVisitor {
        @Override
        public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            final IElementType operationTokenType = expression.getOperationTokenType();
            if (!JavaTokenType.MINUS.equals(operationTokenType)) {
                return;
            }
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) parent;
                final IElementType binaryExpressionTokenType = binaryExpression.getOperationTokenType();
                if (!JavaTokenType.PLUS.equals(binaryExpressionTokenType)) {
                    return;
                }
                final PsiExpression rhs = binaryExpression.getROperand();
                if (!expression.equals(rhs)) {
                    return;
                }
                registerError(expression.getOperationSign());
            }
            else if (parent instanceof PsiAssignmentExpression) {
                final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression) parent;
                final IElementType assignmentTokenType = assignmentExpression.getOperationTokenType();
                if (!JavaTokenType.PLUSEQ.equals(assignmentTokenType)) {
                    return;
                }
                registerError(expression.getOperationSign());
            }
        }
    }
}