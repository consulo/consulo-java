/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.intellij.java.analysis.impl.codeInspection.EquivalenceChecker;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.CleanupLocalInspectionTool;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;

@ExtensionImpl
public class ConditionalExpressionWithIdenticalBranchesInspection extends BaseInspection implements CleanupLocalInspectionTool {
    public boolean myReportOnlyExactlyIdentical;

    @Nullable
    @Override
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Report only exactly identical branches", this, "myReportOnlyExactlyIdentical");
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.conditionalExpressionWithIdenticalBranchesDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        final EquivalenceChecker.Decision decision = (EquivalenceChecker.Decision) infos[1];
        return decision.isExact()
            ? InspectionGadgetsLocalize.conditionalExpressionWithIdenticalBranchesProblemDescriptor().get()
            : InspectionGadgetsLocalize.conditionalExpressionWithSimilarBranchesProblemDescriptor().get();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new CollapseConditional((PsiConditionalExpression) infos[0]);
    }

    private static class CollapseConditional extends InspectionGadgetsFix {
        private final SmartPsiElementPointer<PsiConditionalExpression> myConditionalExpression;

        public CollapseConditional(PsiConditionalExpression expression) {
            myConditionalExpression = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
        }

        @Override
        @Nonnull
        public LocalizeValue getName() {
            return getEquivalenceDecision().getExactlyMatches()
                ? InspectionGadgetsLocalize.conditionalExpressionWithIdenticalBranchesCollapseQuickfix()
                : InspectionGadgetsLocalize.conditionalExpressionWithIdenticalBranchesPushInsideQuickfix();
        }

        public PsiConditionalExpression getConditionalExpression() {
            return myConditionalExpression.getElement();
        }

        private EquivalenceChecker.Decision getEquivalenceDecision() {
            return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalentDecision(getConditionalExpression().getThenExpression(), getConditionalExpression().getElseExpression());
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) {
            final EquivalenceChecker.Decision decision = getEquivalenceDecision();
            final PsiConditionalExpression conditionalExpression = getConditionalExpression();
            final PsiExpression thenExpression = conditionalExpression.getThenExpression();
            assert thenExpression != null;
            if (decision.getExactlyMatches()) {
                final PsiConditionalExpression expression = (PsiConditionalExpression) descriptor.getPsiElement();
                final String bodyText = thenExpression.getText();
                PsiReplacementUtil.replaceExpression(expression, bodyText);
            }
            else if (!decision.isExactUnMatches()) {
                final PsiElement leftDiff = decision.getLeftDiff();
                final PsiElement rightDiff = decision.getRightDiff();

                final String expression = "(" + conditionalExpression.getCondition().getText() + " ? " + leftDiff.getText() + " : " + rightDiff.getText() + ")";
                final PsiExpression newConditionalExpression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(expression, conditionalExpression);

                final PsiElement replacedConditionalExpression = leftDiff.replace(newConditionalExpression);
                ParenthesesUtils.removeParentheses((PsiExpression) replacedConditionalExpression, false);
                conditionalExpression.replace(thenExpression);
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ConditionalExpressionWithIdenticalBranchesVisitor();
    }

    private class ConditionalExpressionWithIdenticalBranchesVisitor extends BaseInspectionVisitor {

        @Override
        public void visitConditionalExpression(PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            final PsiExpression thenExpression = expression.getThenExpression();
            final PsiExpression elseExpression = expression.getElseExpression();
            final EquivalenceChecker.Decision decision = EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalentDecision(thenExpression, elseExpression);
            if (thenExpression != null && (myReportOnlyExactlyIdentical ? decision.getExactlyMatches() : !decision.isExactUnMatches())) {
                registerError(expression, expression, decision);
            }
        }
    }
}