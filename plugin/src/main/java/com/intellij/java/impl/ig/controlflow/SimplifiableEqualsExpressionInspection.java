/*
 * Copyright 2011-2013 Bas Leijdekkers
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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SimplifiableEqualsExpressionInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.simplifiableEqualsExpressionDisplayName();
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.simplifiableEqualsExpressionProblemDescriptor(infos[0]).get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new SimplifiableEqualsExpressionFix((String) infos[0]);
    }

    private static class SimplifiableEqualsExpressionFix extends InspectionGadgetsFix {

        private final String myMethodName;

        public SimplifiableEqualsExpressionFix(String methodName) {
            myMethodName = methodName;
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.simplifiableEqualsExpressionQuickfix(myMethodName);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiElement parent = ParenthesesUtils.getParentSkipParentheses(element);
            if (!(parent instanceof PsiPolyadicExpression)) {
                return;
            }
            PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) parent;
            PsiExpression[] operands = polyadicExpression.getOperands();
            if (operands.length != 2) {
                return;
            }
            PsiExpression operand = ParenthesesUtils.stripParentheses(operands[1]);
            @NonNls StringBuilder newExpressionText = new StringBuilder();
            if (operand instanceof PsiPrefixExpression) {
                PsiPrefixExpression prefixExpression = (PsiPrefixExpression) operand;
                if (!JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
                    return;
                }
                newExpressionText.append('!');
                operand = ParenthesesUtils.stripParentheses(prefixExpression.getOperand());
            }
            if (!(operand instanceof PsiMethodCallExpression)) {
                return;
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) operand;
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            String referenceName = methodExpression.getReferenceName();
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            PsiExpressionList argumentList = methodCallExpression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return;
            }
            PsiExpression argument = arguments[0];
            PsiType type = argument.getType();
            if (PsiType.BOOLEAN.equals(type)) {
                Object value = ExpressionUtils.computeConstantExpression(argument);
                if (Boolean.TRUE.equals(value)) {
                    newExpressionText.append("java.lang.Boolean.TRUE");
                }
                else if (Boolean.FALSE.equals(value)) {
                    newExpressionText.append("java.lang.Boolean.FALSE");
                }
                else {
                    newExpressionText.append("java.lang.Boolean.valueOf(").append(argument.getText()).append(')');
                }
            }
            else if (PsiType.BYTE.equals(type)) {
                newExpressionText.append("java.lang.Byte.valueOf(").append(argument.getText()).append(')');
            }
            else if (PsiType.SHORT.equals(type)) {
                newExpressionText.append("java.lang.Short.valueOf(").append(argument.getText()).append(')');
            }
            else if (PsiType.INT.equals(type)) {
                newExpressionText.append("java.lang.Integer.valueOf(").append(argument.getText()).append(')');
            }
            else if (PsiType.LONG.equals(type)) {
                newExpressionText.append("java.lang.Long.valueOf(").append(argument.getText()).append(')');
            }
            else if (PsiType.FLOAT.equals(type)) {
                newExpressionText.append("java.lang.Float.valueOf(").append(argument.getText()).append(')');
            }
            else if (PsiType.DOUBLE.equals(type)) {
                newExpressionText.append("java.lang.Double.valueOf(").append(argument.getText()).append(')');
            }
            else {
                newExpressionText.append(argument.getText());
            }
            newExpressionText.append('.').append(referenceName).append('(').append(qualifier.getText()).append(')');
            replaceExpression(polyadicExpression, newExpressionText.toString());
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SimplifiableEqualsExpressionVisitor();
    }

    private static class SimplifiableEqualsExpressionVisitor extends BaseInspectionVisitor {

        @Override
        public void visitPolyadicExpression(PsiPolyadicExpression expression) {
            super.visitPolyadicExpression(expression);
            IElementType tokenType = expression.getOperationTokenType();
            if (JavaTokenType.ANDAND.equals(tokenType)) {
                PsiExpression[] operands = expression.getOperands();
                if (operands.length != 2) {
                    return;
                }
                PsiExpression lhs = ParenthesesUtils.stripParentheses(operands[0]);
                if (lhs == null) {
                    return;
                }
                PsiVariable variable = getVariableFromNullComparison(lhs, false);
                if (variable == null) {
                    return;
                }
                PsiExpression rhs = ParenthesesUtils.stripParentheses(operands[1]);
                if (!isEqualsConstant(rhs, variable)) {
                    return;
                }
                registerError(lhs, getMethodName((PsiMethodCallExpression) rhs));
            }
            else if (JavaTokenType.OROR.equals(tokenType)) {
                PsiExpression[] operands = expression.getOperands();
                if (operands.length != 2) {
                    return;
                }
                PsiExpression lhs = ParenthesesUtils.stripParentheses(operands[0]);
                if (lhs == null) {
                    return;
                }
                PsiVariable variable = getVariableFromNullComparison(lhs, true);
                if (variable == null) {
                    return;
                }
                PsiExpression rhs = ParenthesesUtils.stripParentheses(operands[1]);
                if (!(rhs instanceof PsiPrefixExpression)) {
                    return;
                }
                PsiPrefixExpression prefixExpression = (PsiPrefixExpression) rhs;
                if (!JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
                    return;
                }
                PsiExpression operand = ParenthesesUtils.stripParentheses(prefixExpression.getOperand());
                if (!isEqualsConstant(operand, variable)) {
                    return;
                }
                registerError(lhs, getMethodName((PsiMethodCallExpression) operand));
            }
        }

        private static String getMethodName(PsiMethodCallExpression methodCallExpression) {
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            return methodExpression.getReferenceName();
        }

        private static boolean isEqualsConstant(PsiExpression expression, PsiVariable variable) {
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) expression;
            PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (!HardcodedMethodConstants.EQUALS.equals(methodName) && !HardcodedMethodConstants.EQUALS_IGNORE_CASE.equals(methodName)) {
                return false;
            }
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) qualifier;
            PsiElement target = referenceExpression.resolve();
            if (!variable.equals(target)) {
                return false;
            }
            PsiExpressionList argumentList = methodCallExpression.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            if (arguments.length != 1) {
                return false;
            }
            PsiExpression argument = arguments[0];
            return PsiUtil.isConstantExpression(argument);
        }

        @Nullable
        private static PsiVariable getVariableFromNullComparison(PsiExpression expression, boolean equals) {
            if (!(expression instanceof PsiPolyadicExpression)) {
                return null;
            }
            PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) expression;
            IElementType tokenType = polyadicExpression.getOperationTokenType();
            if (equals) {
                if (!JavaTokenType.EQEQ.equals(tokenType)) {
                    return null;
                }
            }
            else {
                if (!JavaTokenType.NE.equals(tokenType)) {
                    return null;
                }
            }
            PsiExpression[] operands = polyadicExpression.getOperands();
            if (operands.length != 2) {
                return null;
            }
            PsiExpression lhs = ParenthesesUtils.stripParentheses(operands[0]);
            if (lhs == null) {
                return null;
            }
            PsiExpression rhs = ParenthesesUtils.stripParentheses(operands[1]);
            if (rhs == null) {
                return null;
            }
            if (PsiType.NULL.equals(lhs.getType())) {
                if (!(rhs instanceof PsiReferenceExpression)) {
                    return null;
                }
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression) rhs;
                PsiElement target = referenceExpression.resolve();
                if (!(target instanceof PsiVariable)) {
                    return null;
                }
                return (PsiVariable) target;
            }
            else if (PsiType.NULL.equals(rhs.getType())) {
                if (!(lhs instanceof PsiReferenceExpression)) {
                    return null;
                }
                PsiReferenceExpression referenceExpression = (PsiReferenceExpression) lhs;
                PsiElement target = referenceExpression.resolve();
                if (!(target instanceof PsiVariable)) {
                    return null;
                }
                return (PsiVariable) target;
            }
            return null;
        }
    }
}
