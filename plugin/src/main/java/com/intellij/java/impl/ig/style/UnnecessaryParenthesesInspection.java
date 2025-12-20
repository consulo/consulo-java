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
package com.intellij.java.impl.ig.style;

import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.intellij.java.analysis.codeInspection.ParenthesesUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.deadCodeNotWorking.impl.MultipleCheckboxOptionsPanel;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import javax.swing.*;

public abstract class UnnecessaryParenthesesInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean ignoreClarifyingParentheses = false;

    @SuppressWarnings({"PublicField"})
    public boolean ignoreParenthesesOnConditionals = false;

    @SuppressWarnings("PublicField")
    public boolean ignoreParenthesesOnLambdaParameter = false;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessaryParenthesesDisplayName();
    }

    @Override
    @Nonnull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsLocalize.unnecessaryParenthesesProblemDescriptor().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsLocalize.unnecessaryParenthesesOption().get(), "ignoreClarifyingParentheses");
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.unnecessaryParenthesesConditionalOption().get(),
            "ignoreParenthesesOnConditionals"
        );
        optionsPanel.addCheckbox(
            InspectionGadgetsLocalize.ignoreParenthesesAroundSingleNoFormalTypeLambdaParameter().get(),
            "ignoreParenthesesOnLambdaParameter"
        );
        return optionsPanel;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryParenthesesVisitor();
    }

    private class UnnecessaryParenthesesFix extends InspectionGadgetsFix {
        @Nonnull
        public LocalizeValue getName() {
            return InspectionGadgetsLocalize.unnecessaryParenthesesRemoveQuickfix();
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element instanceof PsiParameterList) {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(element.getProject());
                PsiParameterList parameterList = (PsiParameterList) element;
                String text = parameterList.getParameters()[0].getName() + "->{}";
                PsiLambdaExpression expression = (PsiLambdaExpression) factory.createExpressionFromText(text, element);
                element.replace(expression.getParameterList());
            }
            else {
                ParenthesesUtils.removeParentheses((PsiExpression) element, ignoreClarifyingParentheses);
            }
        }
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryParenthesesFix();
    }

    private class UnnecessaryParenthesesVisitor extends BaseInspectionVisitor {
        @Override
        public void visitParameterList(PsiParameterList list) {
            super.visitParameterList(list);
            if (!ignoreParenthesesOnLambdaParameter && list.getParent() instanceof PsiLambdaExpression && list.getParametersCount() == 1) {
                PsiParameter parameter = list.getParameters()[0];
                if (parameter.getTypeElement() == null && list.getFirstChild() != parameter && list.getLastChild() != parameter) {
                    registerError(list);
                }
            }
        }

        @Override
        public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
            PsiElement parent = expression.getParent();
            if (parent instanceof PsiParenthesizedExpression) {
                return;
            }
            if (ignoreParenthesesOnConditionals && parent instanceof PsiConditionalExpression) {
                PsiConditionalExpression conditionalExpression = (PsiConditionalExpression) parent;
                PsiExpression condition = conditionalExpression.getCondition();
                if (expression == condition) {
                    return;
                }
            }
            if (!ParenthesesUtils.areParenthesesNeeded(expression, ignoreClarifyingParentheses)) {
                registerError(expression);
                return;
            }
            super.visitParenthesizedExpression(expression);
        }
    }
}
