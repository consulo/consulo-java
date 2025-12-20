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
import org.intellij.lang.annotations.Pattern;

@ExtensionImpl
public class UnnecessaryConditionalExpressionInspection extends BaseInspection {
    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "RedundantConditionalExpression";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessaryConditionalExpressionDisplayName();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryConditionalExpressionVisitor();
    }

    @Nonnull
    public String buildErrorString(Object... infos) {
        PsiConditionalExpression expression = (PsiConditionalExpression) infos[0];
        return InspectionGadgetsLocalize.simplifiableConditionalExpressionProblemDescriptor(calculateReplacementExpression(expression))
            .get();
    }

    static String calculateReplacementExpression(
        PsiConditionalExpression exp
    ) {
        PsiExpression thenExpression = exp.getThenExpression();
        PsiExpression elseExpression = exp.getElseExpression();
        PsiExpression condition = exp.getCondition();

        if (BoolUtils.isFalse(thenExpression) &&
            BoolUtils.isTrue(elseExpression)) {
            return BoolUtils.getNegatedExpressionText(condition);
        }
        else {
            return condition.getText();
        }
    }

    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryConditionalFix();
    }

    private static class UnnecessaryConditionalFix
        extends InspectionGadgetsFix {

        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.constantConditionalExpressionSimplifyQuickfix();
        }

        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            PsiConditionalExpression expression = (PsiConditionalExpression) descriptor.getPsiElement();
            String newExpression = calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private static class UnnecessaryConditionalExpressionVisitor
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
            PsiExpression elseExpression = expression.getElseExpression();
            if (elseExpression == null) {
                return;
            }
            if (BoolUtils.isFalse(thenExpression) &&
                BoolUtils.isTrue(elseExpression) ||
                BoolUtils.isTrue(thenExpression) &&
                    BoolUtils.isFalse(elseExpression)) {
                registerError(expression, expression);
            }
        }
    }
}