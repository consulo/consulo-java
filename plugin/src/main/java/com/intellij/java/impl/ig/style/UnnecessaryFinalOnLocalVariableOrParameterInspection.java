/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.java.impl.ig.fixes.RemoveModifierFix;
import com.intellij.java.language.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;

@ExtensionImpl
public class UnnecessaryFinalOnLocalVariableOrParameterInspection extends BaseInspection {
    @SuppressWarnings({"PublicField"})
    public boolean onlyWarnOnAbstractMethods = false;

    @SuppressWarnings("PublicField")
    public boolean reportLocalVariables = true;

    @SuppressWarnings("PublicField")
    public boolean reportParameters = true;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.unnecessaryFinalOnLocalVariableOrParameterDisplayName();
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public String buildErrorString(Object... infos) {
        PsiVariable variable = (PsiVariable) infos[0];
        String variableName = variable.getName();
        return variable instanceof PsiParameter
            ? InspectionGadgetsLocalize.unnecessaryFinalOnParameterProblemDescriptor(variableName).get()
            : InspectionGadgetsLocalize.unnecessaryFinalOnLocalVariableProblemDescriptor(variableName).get();
    }

    @Override
    @Nullable
    public JComponent createOptionsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        JCheckBox abstractOnlyCheckBox =
            new JCheckBox(InspectionGadgetsLocalize.unnecessaryFinalOnParameterOnlyInterfaceOption().get(), onlyWarnOnAbstractMethods) {
                @Override
                public void setEnabled(boolean b) {
                    // hack to display correctly on initial opening of
                    // inspection settings (otherwise it is always enabled)
                    if (b) {
                        super.setEnabled(reportParameters);
                    }
                    else {
                        super.setEnabled(false);
                    }
                }
            };
        abstractOnlyCheckBox.setEnabled(true);
        abstractOnlyCheckBox.addChangeListener(e -> onlyWarnOnAbstractMethods = abstractOnlyCheckBox.isSelected());
        JCheckBox reportLocalVariablesCheckBox =
            new JCheckBox(InspectionGadgetsLocalize.unnecessaryFinalReportLocalVariablesOption().get(), reportLocalVariables);
        JCheckBox reportParametersCheckBox =
            new JCheckBox(InspectionGadgetsLocalize.unnecessaryFinalReportParametersOption().get(), reportParameters);

        reportLocalVariablesCheckBox.addChangeListener(e -> {
            reportLocalVariables = reportLocalVariablesCheckBox.isSelected();
            if (!reportLocalVariables) {
                reportParametersCheckBox.setSelected(true);
            }
        });
        reportParametersCheckBox.addChangeListener(e -> {
            reportParameters = reportParametersCheckBox.isSelected();
            if (!reportParameters) {
                reportLocalVariablesCheckBox.setSelected(true);
            }
            abstractOnlyCheckBox.setEnabled(reportParameters);
        });
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        panel.add(reportLocalVariablesCheckBox, constraints);
        constraints.gridy = 1;
        panel.add(reportParametersCheckBox, constraints);
        constraints.insets.left = 20;
        constraints.gridy = 2;
        constraints.weighty = 1.0;
        panel.add(abstractOnlyCheckBox, constraints);
        return panel;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryFinalOnLocalVariableOrParameterVisitor();
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new RemoveModifierFix((String) infos[1]);
    }

    private class UnnecessaryFinalOnLocalVariableOrParameterVisitor extends BaseInspectionVisitor {
        @Override
        public void visitDeclarationStatement(@Nonnull PsiDeclarationStatement statement) {
            super.visitDeclarationStatement(statement);
            if (!reportLocalVariables) {
                return;
            }
            PsiElement[] declaredElements = statement.getDeclaredElements();
            if (declaredElements.length == 0) {
                return;
            }
            for (PsiElement declaredElement : declaredElements) {
                if (!(declaredElement instanceof PsiLocalVariable variable && variable.hasModifierProperty(PsiModifier.FINAL))) {
                    return;
                }
            }
            PsiCodeBlock containingBlock = PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
            if (containingBlock == null) {
                return;
            }
            for (PsiElement declaredElement : declaredElements) {
                PsiLocalVariable variable = (PsiLocalVariable) declaredElement;
                if (VariableAccessUtils.variableIsUsedInInnerClass(variable, containingBlock)) {
                    return;
                }
            }
            PsiLocalVariable variable = (PsiLocalVariable) statement.getDeclaredElements()[0];
            registerModifierError(PsiModifier.FINAL, variable, variable, PsiModifier.FINAL);
        }

        @Override
        public void visitMethod(@Nonnull PsiMethod method) {
            super.visitMethod(method);
            if (!reportParameters) {
                return;
            }
            PsiParameterList parameterList = method.getParameterList();
            PsiParameter[] parameters = parameterList.getParameters();
            for (PsiParameter parameter : parameters) {
                checkParameter(method, parameter);
            }
        }

        private void checkParameter(PsiMethod method, PsiParameter parameter) {
            if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                if (containingClass.isInterface() || containingClass.isAnnotationType()) {
                    registerModifierError(PsiModifier.FINAL, parameter, parameter, PsiModifier.FINAL);
                    return;
                }
            }
            if (method.isAbstract()) {
                registerModifierError(PsiModifier.FINAL, parameter, parameter, PsiModifier.FINAL);
                return;
            }
            if (onlyWarnOnAbstractMethods) {
                return;
            }
            if (VariableAccessUtils.variableIsUsedInInnerClass(parameter, method)) {
                return;
            }
            registerModifierError(PsiModifier.FINAL, parameter, parameter, PsiModifier.FINAL);
        }

        @Override
        public void visitTryStatement(@Nonnull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            if (onlyWarnOnAbstractMethods || !reportParameters) {
                return;
            }
            PsiCatchSection[] catchSections = statement.getCatchSections();
            for (PsiCatchSection catchSection : catchSections) {
                PsiParameter parameter = catchSection.getParameter();
                PsiCodeBlock catchBlock = catchSection.getCatchBlock();
                if (parameter == null || catchBlock == null) {
                    continue;
                }
                if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
                    continue;
                }
                if (!VariableAccessUtils.variableIsUsedInInnerClass(parameter, catchBlock)) {
                    registerModifierError(PsiModifier.FINAL, parameter, parameter, PsiModifier.FINAL);
                }
            }
        }

        @Override
        public void visitForeachStatement(@Nonnull PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            if (onlyWarnOnAbstractMethods || !reportParameters) {
                return;
            }
            PsiParameter parameter = statement.getIterationParameter();
            if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            if (VariableAccessUtils.variableIsUsedInInnerClass(parameter, statement)) {
                return;
            }
            registerModifierError(PsiModifier.FINAL, parameter, parameter, PsiModifier.FINAL);
        }
    }
}