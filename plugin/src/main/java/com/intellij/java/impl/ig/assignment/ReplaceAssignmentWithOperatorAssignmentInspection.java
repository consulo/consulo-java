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
package com.intellij.java.impl.ig.assignment;

import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;

@ExtensionImpl
public class ReplaceAssignmentWithOperatorAssignmentInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean ignoreLazyOperators = true;

    /**
     * @noinspection PublicField
     */
    public boolean ignoreObscureOperators = false;

    @Nonnull
    @Override
    @Pattern("[a-zA-Z_0-9.]+")
    public String getID() {
        return "AssignmentReplaceableWithOperatorAssignment";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.assignmentReplaceableWithOperatorAssignmentDisplayName();
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public String buildErrorString(Object... infos) {
        PsiExpression lhs = (PsiExpression) infos[0];
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression) infos[1];
        return InspectionGadgetsLocalize.assignmentReplaceableWithOperatorAssignmentProblemDescriptor(
            calculateReplacementExpression(lhs, polyadicExpression)
        ).get();
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.assignmentReplaceableWithOperatorAssignmentIgnoreConditionalOperatorsOption().get(),
            "ignoreLazyOperators"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.assignmentReplaceableWithOperatorAssignmentIgnoreObscureOperatorsOption().get(),
            "ignoreObscureOperators"
        );
        return optionsPanel;
    }

    @RequiredReadAction
    static String calculateReplacementExpression(PsiExpression lhs, PsiPolyadicExpression polyadicExpression) {
        PsiExpression[] operands = polyadicExpression.getOperands();
        PsiJavaToken sign = polyadicExpression.getTokenBeforeOperand(operands[1]);
        String signText = sign.getText();
        if ("&&".equals(signText)) {
            signText = "&";
        }
        else if ("||".equals(signText)) {
            signText = "|";
        }
        StringBuilder text = new StringBuilder(lhs.getText()).append(' ').append(signText).append("= ");
        boolean addToken = false;
        for (int i = 1; i < operands.length; i++) {
            PsiExpression operand = operands[i];
            if (addToken) {
                PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
                text.append(' ');
                if (token != null) {
                    text.append(token.getText());
                }
                text.append(' ');
            }
            else {
                addToken = true;
            }
            text.append(operand.getText());
        }
        return text.toString();
    }

    @Override
    @RequiredReadAction
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new ReplaceAssignmentWithOperatorAssignmentFix((PsiPolyadicExpression) infos[1]);
    }

    private static class ReplaceAssignmentWithOperatorAssignmentFix extends InspectionGadgetsFix {
        @Nonnull
        private final LocalizeValue myName;

        @RequiredReadAction
        private ReplaceAssignmentWithOperatorAssignmentFix(PsiPolyadicExpression expression) {
            PsiJavaToken sign = expression.getTokenBeforeOperand(expression.getOperands()[1]);
            String signText = sign.getText();
            if ("&&".equals(signText)) {
                signText = "&";
            }
            else if ("||".equals(signText)) {
                signText = "|";
            }
            myName = InspectionGadgetsLocalize.assignmentReplaceableWithOperatorReplaceQuickfix(signText);
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return myName;
        }

        @Override
        @RequiredWriteAction
        public void doFix(@Nonnull Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            if (!(descriptor.getPsiElement() instanceof PsiAssignmentExpression expression)) {
                return;
            }
            PsiExpression lhs = expression.getLExpression();
            PsiExpression rhs = ParenthesesUtils.stripParentheses(expression.getRExpression());
            if (rhs instanceof PsiTypeCastExpression typeCastExpression) {
                PsiType castType = typeCastExpression.getType();
                if (castType == null || !castType.equals(lhs.getType())) {
                    return;
                }
                rhs = ParenthesesUtils.stripParentheses(typeCastExpression.getOperand());
            }
            if (!(rhs instanceof PsiPolyadicExpression polyadicExpression)) {
                return;
            }
            String newExpression = calculateReplacementExpression(lhs, polyadicExpression);
            replaceExpression(expression, newExpression);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ReplaceAssignmentWithOperatorAssignmentVisitor();
    }

    private class ReplaceAssignmentWithOperatorAssignmentVisitor extends BaseInspectionVisitor {
        @Override
        public void visitAssignmentExpression(@Nonnull PsiAssignmentExpression assignment) {
            super.visitAssignmentExpression(assignment);
            IElementType assignmentTokenType = assignment.getOperationTokenType();
            if (!assignmentTokenType.equals(JavaTokenType.EQ)) {
                return;
            }
            PsiExpression lhs = assignment.getLExpression();
            PsiExpression rhs = ParenthesesUtils.stripParentheses(assignment.getRExpression());
            if (rhs instanceof PsiTypeCastExpression typeCastExpression) {
                PsiType castType = typeCastExpression.getType();
                if (castType == null || !castType.equals(lhs.getType())) {
                    return;
                }
                rhs = ParenthesesUtils.stripParentheses(typeCastExpression.getOperand());
            }
            if (!(rhs instanceof PsiPolyadicExpression polyadicExpression)) {
                return;
            }
            PsiExpression[] operands = polyadicExpression.getOperands();
            if (operands.length < 2) {
                return;
            }
            if (operands.length > 2 && !ParenthesesUtils.isAssociativeOperation(polyadicExpression)) {
                return;
            }
            for (PsiExpression operand : operands) {
                if (operand == null) {
                    return;
                }
            }
            IElementType expressionTokenType = polyadicExpression.getOperationTokenType();
            if (JavaTokenType.EQEQ.equals(expressionTokenType) || JavaTokenType.NE.equals(expressionTokenType)) {
                return;
            }
            if (ignoreLazyOperators) {
                if (JavaTokenType.ANDAND.equals(expressionTokenType) || JavaTokenType.OROR.equals(expressionTokenType)) {
                    return;
                }
            }
            if (ignoreObscureOperators) {
                if (JavaTokenType.XOR.equals(expressionTokenType) || JavaTokenType.PERC.equals(expressionTokenType)) {
                    return;
                }
            }
            if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(lhs, operands[0])) {
                return;
            }
            registerError(assignment, lhs, polyadicExpression);
        }
    }
}