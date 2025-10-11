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
package com.intellij.java.impl.ig.controlflow;

import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.SingleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;

@ExtensionImpl
public class NegatedConditionalInspection extends BaseInspection {
    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreNegatedNullComparison = true;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "ConditionalExpressionWithNegatedCondition";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.negatedConditionalDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.negatedConditionalProblemDescriptor().get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new NegatedConditionalVisitor();
    }

    @Override
    public JComponent createOptionsPanel() {
        LocalizeValue message = InspectionGadgetsLocalize.negatedConditionalIgnoreOption();
        return new SingleCheckboxOptionsPanel(message.get(), this, "m_ignoreNegatedNullComparison");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new NegatedConditionalFix();
    }

    private static class NegatedConditionalFix extends InspectionGadgetsFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.negatedConditionalInvertQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression) element.getParent();
            assert conditionalExpression != null;
            final PsiExpression elseBranch = conditionalExpression.getElseExpression();
            final PsiExpression thenBranch = conditionalExpression.getThenExpression();
            final PsiExpression condition = conditionalExpression.getCondition();
            final String negatedCondition = BoolUtils.getNegatedExpressionText(condition);
            assert elseBranch != null;
            assert thenBranch != null;
            final String newStatement = negatedCondition + '?' + elseBranch.getText() + ':' + thenBranch.getText();
            replaceExpression(conditionalExpression, newStatement);
        }
    }

    private class NegatedConditionalVisitor extends BaseInspectionVisitor {

        @Override
        public void visitConditionalExpression(PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            final PsiExpression thenBranch = expression.getThenExpression();
            if (thenBranch == null) {
                return;
            }
            final PsiExpression elseBranch = expression.getElseExpression();
            if (elseBranch == null) {
                return;
            }
            final PsiExpression condition = expression.getCondition();
            if (!ExpressionUtils.isNegation(condition, m_ignoreNegatedNullComparison, false)) {
                return;
            }
            registerError(condition);
        }
    }
}