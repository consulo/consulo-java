/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.intellij.java.impl.ig.methodmetrics;

import com.intellij.java.language.psi.PsiMethod;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

@ExtensionImpl
public class MethodCouplingInspection extends MethodMetricInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_includeJavaClasses = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_includeLibraryClasses = false;

  @Override
  @Nonnull
  public String getID() {
    return "OverlyCoupledMethod";
  }

  @Override
  @Nonnull
  public LocalizeValue getDisplayName() {
    return InspectionGadgetsLocalize.methodCouplingDisplayName();
  }

  @Override
  @Nonnull
  public String buildErrorString(Object... infos) {
    Integer coupling = (Integer)infos[0];
    return InspectionGadgetsLocalize.methodCouplingProblemDescriptor(coupling).get();
  }

  @Override
  protected int getDefaultLimit() {
    return 10;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsLocalize.methodCouplingLimitOption().get();
  }

  @Override
  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    String configurationLabel = getConfigurationLabel();
    JLabel label = new JLabel(configurationLabel);

    JFormattedTextField valueField = prepareNumberEditor(() -> m_limit, i -> m_limit = i);

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 0.0;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.NONE;
    panel.add(label, constraints);
    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.weightx = 1.0;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.NONE;
    panel.add(valueField, constraints);

    CheckBox arrayCheckBox = new CheckBox(
      InspectionGadgetsLocalize.includeJavaSystemClassesOption().get(),
      this,
      "m_includeJavaClasses"
    );
    CheckBox objectCheckBox = new CheckBox(
      InspectionGadgetsLocalize.includeLibraryClassesOption().get(),
      this,
      "m_includeLibraryClasses"
    );
    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.gridwidth = 2;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(arrayCheckBox, constraints);

    constraints.gridx = 0;
    constraints.gridy = 2;
    constraints.gridwidth = 2;
    constraints.weighty = 1.0;
    panel.add(objectCheckBox, constraints);
    return panel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodCouplingVisitor();
  }

  private class MethodCouplingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@Nonnull PsiMethod method) {
      // note: no call to super
      if (method.getNameIdentifier() == null) {
        return;
      }
      CouplingVisitor visitor = new CouplingVisitor(
        method, m_includeJavaClasses, m_includeLibraryClasses);
      method.accept(visitor);
      int coupling = visitor.getNumDependencies();

      if (coupling <= getLimit()) {
        return;
      }
      registerMethodError(method, Integer.valueOf(coupling));
    }
  }
}