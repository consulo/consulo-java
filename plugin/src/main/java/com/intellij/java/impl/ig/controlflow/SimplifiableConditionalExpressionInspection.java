/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

@ExtensionImpl
public class SimplifiableConditionalExpressionInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.simplifiableConditionalExpressionDisplayName();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiConditionalExpression expression = (PsiConditionalExpression) infos[0];
        return InspectionGadgetsLocalize.simplifiableConditionalExpressionProblemDescriptor(
            calculateReplacementExpression(expression)
        ).get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new SimplifiableConditionalFix();
    }

    private static class SimplifiableConditionalFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.constantConditionalExpressionSimplifyQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiConditionalExpression expression = (PsiConditionalExpression) descriptor.getPsiElement();
            String newExpression = calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new SimplifiableConditionalExpressionVisitor();
    }

    @NonNls
    static String calculateReplacementExpression(
        PsiConditionalExpression expression
    ) {
        PsiExpression thenExpression = expression.getThenExpression();
        PsiExpression elseExpression = expression.getElseExpression();
        PsiExpression condition = expression.getCondition();
        assert thenExpression != null;
        assert elseExpression != null;

        String elseText = elseExpression.getText();
        String conditionText = condition.getText();
        if (BoolUtils.isTrue(thenExpression)) {
            @NonNls String elseExpressionText;
            if (ParenthesesUtils.getPrecedence(elseExpression) >
                ParenthesesUtils.OR_PRECEDENCE) {
                elseExpressionText = '(' + elseText + ')';
            }
            else {
                elseExpressionText = elseText;
            }
            if (ParenthesesUtils.getPrecedence(condition) > ParenthesesUtils.OR_PRECEDENCE) {
                conditionText = "(" + conditionText + ")";
            }
            return conditionText + " || " + elseExpressionText;
        }
        else if (BoolUtils.isFalse(thenExpression)) {
            @NonNls String elseExpressionText;
            if (ParenthesesUtils.getPrecedence(elseExpression) >
                ParenthesesUtils.AND_PRECEDENCE) {
                elseExpressionText = '(' + elseText + ')';
            }
            else {
                elseExpressionText = elseText;
            }
            return BoolUtils.getNegatedExpressionText(condition) + " && " +
                elseExpressionText;
        }
        String thenText = thenExpression.getText();
        if (BoolUtils.isFalse(elseExpression)) {
            @NonNls String thenExpressionText;
            if (ParenthesesUtils.getPrecedence(thenExpression) >
                ParenthesesUtils.AND_PRECEDENCE) {
                thenExpressionText = '(' + thenText + ')';
            }
            else {
                thenExpressionText = thenText;
            }
            if (ParenthesesUtils.getPrecedence(condition) > ParenthesesUtils.AND_PRECEDENCE) {
                conditionText = "(" + conditionText + ")";
            }
            return conditionText + " && " + thenExpressionText;
        }
        else {
            @NonNls String thenExpressionText;
            if (ParenthesesUtils.getPrecedence(thenExpression) >
                ParenthesesUtils.OR_PRECEDENCE) {
                thenExpressionText = '(' + thenText + ')';
            }
            else {
                thenExpressionText = thenText;
            }
            return BoolUtils.getNegatedExpressionText(condition) + " || " +
                thenExpressionText;
        }
    }

    private static class SimplifiableConditionalExpressionVisitor
        extends BaseInspectionVisitor {

        @Override
        public void visitConditionalExpression(
            PsiConditionalExpression expression
        ) {
            super.visitConditionalExpression(expression);
            PsiExpression thenExpression = expression.getThenExpression();
            if (thenExpression == null) {
                return;
            }
            PsiType thenType = thenExpression.getType();
            if (!PsiType.BOOLEAN.equals(thenType)) {
                return;
            }
            PsiExpression elseExpression = expression.getElseExpression();
            if (elseExpression == null) {
                return;
            }
            PsiType elseType = elseExpression.getType();
            if (!PsiType.BOOLEAN.equals(elseType)) {
                return;
            }
            boolean thenConstant = BoolUtils.isFalse(thenExpression) ||
                BoolUtils.isTrue(thenExpression);
            boolean elseConstant = BoolUtils.isFalse(elseExpression) ||
                BoolUtils.isTrue(elseExpression);
            if (thenConstant == elseConstant) {
                return;
            }
            registerError(expression, expression);
        }
    }
}