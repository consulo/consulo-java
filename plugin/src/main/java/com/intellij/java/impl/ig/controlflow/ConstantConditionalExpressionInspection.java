/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class ConstantConditionalExpressionInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.constantConditionalExpressionDisplayName();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        final PsiConditionalExpression expression = (PsiConditionalExpression) infos[0];
        return InspectionGadgetsLocalize.constantConditionalExpressionProblemDescriptor(calculateReplacementExpression(expression)).get();
    }

    static String calculateReplacementExpression(
        PsiConditionalExpression exp
    ) {
        final PsiExpression thenExpression = exp.getThenExpression();
        final PsiExpression elseExpression = exp.getElseExpression();
        final PsiExpression condition = exp.getCondition();
        assert thenExpression != null;
        assert elseExpression != null;
        if (BoolUtils.isTrue(condition)) {
            return thenExpression.getText();
        }
        else {
            return elseExpression.getText();
        }
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new ConstantConditionalFix();
    }

    private static class ConstantConditionalFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.constantConditionalExpressionSimplifyQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            final PsiConditionalExpression expression = (PsiConditionalExpression) descriptor.getPsiElement();
            final String newExpression = calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ConstantConditionalExpressionVisitor();
    }

    private static class ConstantConditionalExpressionVisitor extends BaseInspectionVisitor {
        @Override
        public void visitConditionalExpression(PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            final PsiExpression condition = expression.getCondition();
            final PsiExpression thenExpression = expression.getThenExpression();
            if (thenExpression == null) {
                return;
            }
            final PsiExpression elseExpression = expression.getElseExpression();
            if (elseExpression == null) {
                return;
            }
            if (BoolUtils.isFalse(condition) || BoolUtils.isTrue(condition)) {
                registerError(expression, expression);
            }
        }
    }
}