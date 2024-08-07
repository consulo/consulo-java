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
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

@ExtensionImpl
public class UnnecessaryFinalOnLocalVariableOrParameterInspection
  extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean onlyWarnOnAbstractMethods = false;

  @SuppressWarnings("PublicField")
  public boolean reportLocalVariables = true;

  @SuppressWarnings("PublicField")
  public boolean reportParameters = true;

  @Override
  @Nonnull
  public String getDisplayName() {
    return InspectionGadgetsLocalize.unnecessaryFinalOnLocalVariableOrParameterDisplayName().get();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    final PsiVariable variable = (PsiVariable)infos[0];
    final String variableName = variable.getName();
    return variable instanceof PsiParameter
      ? InspectionGadgetsLocalize.unnecessaryFinalOnParameterProblemDescriptor(variableName).get()
      : InspectionGadgetsLocalize.unnecessaryFinalOnLocalVariableProblemDescriptor(variableName).get();
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final JCheckBox abstractOnlyCheckBox =
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
    abstractOnlyCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        onlyWarnOnAbstractMethods = abstractOnlyCheckBox.isSelected();
      }
    });
    final JCheckBox reportLocalVariablesCheckBox =
      new JCheckBox(InspectionGadgetsLocalize.unnecessaryFinalReportLocalVariablesOption().get(), reportLocalVariables);
    final JCheckBox reportParametersCheckBox =
      new JCheckBox(InspectionGadgetsLocalize.unnecessaryFinalReportParametersOption().get(), reportParameters);

    reportLocalVariablesCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        reportLocalVariables =
          reportLocalVariablesCheckBox.isSelected();
        if (!reportLocalVariables) {
          reportParametersCheckBox.setSelected(true);
        }
      }
    });
    reportParametersCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        reportParameters =
          reportParametersCheckBox.isSelected();
        if (!reportParameters) {
          reportLocalVariablesCheckBox.setSelected(true);
        }
        abstractOnlyCheckBox.setEnabled(reportParameters);
      }
    });
    final GridBagConstraints constraints = new GridBagConstraints();
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
    return new RemoveModifierFix((String)infos[1]);
  }

  private class UnnecessaryFinalOnLocalVariableOrParameterVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitDeclarationStatement(
      PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      if (!reportLocalVariables) {
        return;
      }
      final PsiElement[] declaredElements =
        statement.getDeclaredElements();
      if (declaredElements.length == 0) {
        return;
      }
      for (final PsiElement declaredElement : declaredElements) {
        if (!(declaredElement instanceof PsiLocalVariable)) {
          return;
        }
        final PsiLocalVariable variable =
          (PsiLocalVariable)declaredElement;
        if (!variable.hasModifierProperty(PsiModifier.FINAL)) {
          return;
        }
      }
      final PsiCodeBlock containingBlock =
        PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
      if (containingBlock == null) {
        return;
      }
      for (PsiElement declaredElement : declaredElements) {
        final PsiLocalVariable variable =
          (PsiLocalVariable)declaredElement;
        if (VariableAccessUtils.variableIsUsedInInnerClass(variable,
                                                           containingBlock)) {
          return;
        }
      }
      final PsiLocalVariable variable =
        (PsiLocalVariable)statement.getDeclaredElements()[0];
      registerModifierError(PsiModifier.FINAL, variable, variable,
                            PsiModifier.FINAL);
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!reportParameters) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      for (final PsiParameter parameter : parameters) {
        checkParameter(method, parameter);
      }
    }

    private void checkParameter(PsiMethod method, PsiParameter parameter) {
      if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        if (containingClass.isInterface() ||
            containingClass.isAnnotationType()) {
          registerModifierError(PsiModifier.FINAL, parameter,
                                parameter, PsiModifier.FINAL);
          return;
        }
      }
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        registerModifierError(PsiModifier.FINAL, parameter,
                              parameter, PsiModifier.FINAL);
        return;
      }
      if (onlyWarnOnAbstractMethods) {
        return;
      }
      if (VariableAccessUtils.variableIsUsedInInnerClass(parameter,
                                                         method)) {
        return;
      }
      registerModifierError(PsiModifier.FINAL, parameter,
                            parameter, PsiModifier.FINAL);
    }

    @Override
    public void visitTryStatement(PsiTryStatement statement) {
      super.visitTryStatement(statement);
      if (onlyWarnOnAbstractMethods || !reportParameters) {
        return;
      }
      final PsiCatchSection[] catchSections =
        statement.getCatchSections();
      for (PsiCatchSection catchSection : catchSections) {
        final PsiParameter parameter = catchSection.getParameter();
        final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
        if (parameter == null || catchBlock == null) {
          continue;
        }
        if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
          continue;
        }
        if (!VariableAccessUtils.variableIsUsedInInnerClass(
          parameter, catchBlock)) {
          registerModifierError(PsiModifier.FINAL, parameter,
                                parameter, PsiModifier.FINAL);
        }
      }
    }

    @Override
    public void visitForeachStatement(
      PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      if (onlyWarnOnAbstractMethods || !reportParameters) {
        return;
      }
      final PsiParameter parameter = statement.getIterationParameter();
      if (!parameter.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (VariableAccessUtils.variableIsUsedInInnerClass(parameter,
                                                         statement)) {
        return;
      }
      registerModifierError(PsiModifier.FINAL, parameter,
                            parameter, PsiModifier.FINAL);
    }
  }
}