/*
 * Copyright 2008-2009 Bas Leijdekkers
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
package com.intellij.java.impl.ig.dataflow;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class ConstantValueVariableUseInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.constantValueVariableUseDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.constantValueVariableUseProblemDescriptor().get();
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        PsiExpression expression = (PsiExpression) infos[0];
        return new ReplaceReferenceWithExpressionFix(expression);
    }

    private static class ReplaceReferenceWithExpressionFix
        extends InspectionGadgetsFix {
        private final SmartPsiElementPointer<PsiExpression> expression;
        private final String myText;

        ReplaceReferenceWithExpressionFix(
            PsiExpression expression
        ) {
            this.expression = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
            myText = expression.getText();
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.replaceReferenceWithExpressionQuickfix(myText);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();

            PsiExpression exp = expression.getElement();
            if (exp == null) {
                return;
            }
            element.replace(exp);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ConstantValueVariableUseVisitor();
    }

    private static class ConstantValueVariableUseVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            PsiExpression condition = statement.getCondition();
            PsiStatement body = statement.getThenBranch();
            checkCondition(condition, body);
        }

        @Override
        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            PsiExpression condition = statement.getCondition();
            PsiStatement body = statement.getBody();
            checkCondition(condition, body);
        }

        @Override
        public void visitForStatement(PsiForStatement statement) {
            super.visitForStatement(statement);
            PsiExpression condition = statement.getCondition();
            PsiStatement body = statement.getBody();
            checkCondition(condition, body);
        }

        private boolean checkCondition(
            @Nullable PsiExpression condition,
            @Nullable PsiStatement body
        ) {
            if (body == null) {
                return false;
            }
            if (!(condition instanceof PsiBinaryExpression)) {
                return false;
            }
            PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) condition;
            IElementType tokenType =
                binaryExpression.getOperationTokenType();
            PsiExpression lhs = binaryExpression.getLOperand();
            PsiExpression rhs = binaryExpression.getROperand();
            if (JavaTokenType.ANDAND == tokenType) {
                return checkCondition(lhs, body) ||
                    checkCondition(rhs, body);
            }
            if (JavaTokenType.EQEQ != tokenType) {
                return false;
            }
            if (rhs == null) {
                return false;
            }
            if (PsiUtil.isConstantExpression(lhs)) {
                return checkConstantValueVariableUse(rhs, lhs, body);
            }
            else if (PsiUtil.isConstantExpression(rhs)) {
                return checkConstantValueVariableUse(lhs, rhs, body);
            }
            return false;
        }

        private boolean checkConstantValueVariableUse(
            @Nullable PsiExpression expression,
            @Nonnull PsiExpression constantExpression,
            @Nonnull PsiElement body
        ) {
            PsiType constantType = constantExpression.getType();
            if (PsiType.DOUBLE.equals(constantType)) {
                Object result = ExpressionUtils.computeConstantExpression(
                    constantExpression, false);
                if (Double.valueOf(0.0).equals(result) ||
                    Double.valueOf(-0.0).equals(result)) {
                    return false;
                }
            }
            if (!(expression instanceof PsiReferenceExpression)) {
                return false;
            }
            PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) expression;
            PsiElement target = referenceExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return false;
            }
            if (target instanceof PsiField) {
                return false;
            }
            PsiVariable variable = (PsiVariable) target;
            VariableReadVisitor visitor =
                new VariableReadVisitor(variable);
            body.accept(visitor);
            if (!visitor.isRead()) {
                return false;
            }
            registerError(visitor.getReference(), constantExpression);
            return true;
        }
    }

    private static class VariableReadVisitor
        extends JavaRecursiveElementVisitor {

        @Nonnull
        private final PsiVariable variable;
        private boolean read = false;
        private boolean written = false;
        private PsiReferenceExpression reference = null;

        VariableReadVisitor(@Nonnull PsiVariable variable) {
            this.variable = variable;
        }

        @Override
        public void visitElement(@Nonnull PsiElement element) {
            if (read || written) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitAssignmentExpression(
            @Nonnull PsiAssignmentExpression assignment
        ) {
            if (read || written) {
                return;
            }
            super.visitAssignmentExpression(assignment);
            PsiExpression lhs = assignment.getLExpression();
            if (lhs instanceof PsiReferenceExpression) {
                PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression) lhs;
                PsiElement target = referenceExpression.resolve();
                if (variable.equals(target)) {
                    written = true;
                    return;
                }
            }
            PsiExpression rhs = assignment.getRExpression();
            if (rhs == null) {
                return;
            }
            VariableUsedVisitor visitor =
                new VariableUsedVisitor(variable);
            rhs.accept(visitor);
            read = visitor.isUsed();
            reference = visitor.getReference();
        }

        @Override
        public void visitPrefixExpression(
            @Nonnull PsiPrefixExpression prefixExpression
        ) {
            if (read || written) {
                return;
            }
            super.visitPrefixExpression(prefixExpression);
            IElementType tokenType = prefixExpression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            PsiExpression operand = prefixExpression.getOperand();
            if (!(operand instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) operand;
            PsiElement target = referenceExpression.resolve();
            if (!variable.equals(target)) {
                return;
            }
            written = true;
        }

        @Override
        public void visitPostfixExpression(
            @Nonnull PsiPostfixExpression postfixExpression
        ) {
            if (read || written) {
                return;
            }
            super.visitPostfixExpression(postfixExpression);
            IElementType tokenType = postfixExpression.getOperationTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                !tokenType.equals(JavaTokenType.MINUSMINUS)) {
                return;
            }
            PsiExpression operand = postfixExpression.getOperand();
            if (!(operand instanceof PsiReferenceExpression)) {
                return;
            }
            PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) operand;
            PsiElement target = referenceExpression.resolve();
            if (!variable.equals(target)) {
                return;
            }
            written = true;
        }

        @Override
        public void visitVariable(@Nonnull PsiVariable variable) {
            if (read || written) {
                return;
            }
            super.visitVariable(variable);
            PsiExpression initalizer = variable.getInitializer();
            if (initalizer == null) {
                return;
            }
            VariableUsedVisitor visitor =
                new VariableUsedVisitor(variable);
            initalizer.accept(visitor);
            read = visitor.isUsed();
            reference = visitor.getReference();
        }

        @Override
        public void visitMethodCallExpression(
            @Nonnull PsiMethodCallExpression call
        ) {
            if (read || written) {
                return;
            }
            super.visitMethodCallExpression(call);
            PsiExpressionList argumentList = call.getArgumentList();
            PsiExpression[] arguments = argumentList.getExpressions();
            for (PsiExpression argument : arguments) {
                VariableUsedVisitor visitor =
                    new VariableUsedVisitor(variable);
                argument.accept(visitor);
                if (visitor.isUsed()) {
                    read = true;
                    reference = visitor.getReference();
                    return;
                }
            }
        }

        @Override
        public void visitNewExpression(
            @Nonnull PsiNewExpression newExpression
        ) {
            if (read || written) {
                return;
            }
            super.visitNewExpression(newExpression);
            PsiExpressionList argumentList =
                newExpression.getArgumentList();
            if (argumentList == null) {
                return;
            }
            PsiExpression[] arguments = argumentList.getExpressions();
            for (PsiExpression argument : arguments) {
                VariableUsedVisitor visitor =
                    new VariableUsedVisitor(variable);
                argument.accept(visitor);
                if (visitor.isUsed()) {
                    read = true;
                    reference = visitor.getReference();
                    return;
                }
            }
        }

        @Override
        public void visitArrayInitializerExpression(
            PsiArrayInitializerExpression expression
        ) {
            if (read || written) {
                return;
            }
            super.visitArrayInitializerExpression(expression);
            PsiExpression[] arguments = expression.getInitializers();
            for (PsiExpression argument : arguments) {
                VariableUsedVisitor visitor =
                    new VariableUsedVisitor(variable);
                argument.accept(visitor);
                if (visitor.isUsed()) {
                    read = true;
                    reference = visitor.getReference();
                    return;
                }
            }
        }

        @Override
        public void visitReturnStatement(
            @Nonnull PsiReturnStatement returnStatement
        ) {
            if (read || written) {
                return;
            }
            super.visitReturnStatement(returnStatement);
            PsiExpression returnValue = returnStatement.getReturnValue();
            if (returnValue == null) {
                return;
            }
            VariableUsedVisitor visitor =
                new VariableUsedVisitor(variable);
            returnValue.accept(visitor);
            read = visitor.isUsed();
            reference = visitor.getReference();
        }

        /**
         * check if variable is used in nested/inner class.
         */
        @Override
        public void visitClass(PsiClass aClass) {
            if (read || written) {
                return;
            }
            super.visitClass(aClass);
            VariableUsedVisitor visitor =
                new VariableUsedVisitor(variable);
            aClass.accept(visitor);
            read = visitor.isUsed();
            reference = visitor.getReference();
        }

        public boolean isRead() {
            return read;
        }

        public PsiReferenceExpression getReference() {
            return reference;
        }
    }

    private static class VariableUsedVisitor
        extends JavaRecursiveElementVisitor {

        private final PsiVariable variable;
        private boolean used = false;
        private PsiReferenceExpression reference = null;

        VariableUsedVisitor(PsiVariable variable) {
            this.variable = variable;
        }

        @Override
        public void visitElement(PsiElement element) {
            if (used) {
                return;
            }
            super.visitElement(element);
        }

        @Override
        public void visitReferenceExpression(
            @Nonnull PsiReferenceExpression expression
        ) {
            if (used) {
                return;
            }
            super.visitReferenceExpression(expression);
            PsiElement referent = expression.resolve();
            if (referent == null) {
                return;
            }
            if (referent.equals(variable)) {
                reference = expression;
                used = true;
            }
        }

        public boolean isUsed() {
            return used;
        }

        public PsiReferenceExpression getReference() {
            return reference;
        }
    }
}
