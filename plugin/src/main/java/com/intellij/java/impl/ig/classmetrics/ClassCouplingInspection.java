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
package com.intellij.java.impl.ig.classmetrics;

import com.intellij.java.language.psi.PsiClass;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.localize.InspectionGadgetsLocalize;
import consulo.annotation.component.ExtensionImpl;
import consulo.deadCodeNotWorking.impl.CheckBox;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.Pattern;

import javax.swing.*;
import java.awt.*;

@ExtensionImpl
public class ClassCouplingInspection extends ClassMetricInspection {
    private static final int DEFAULT_COUPLING_LIMIT = 15;
    /**
     * @noinspection PublicField
     */
    public boolean m_includeJavaClasses = false;
    /**
     * @noinspection PublicField
     */
    public boolean m_includeLibraryClasses = false;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "OverlyCoupledClass";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.overlyCoupledClassDisplayName();
    }

    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        final Integer totalDependencies = (Integer) infos[0];
        return InspectionGadgetsLocalize.overlyCoupledClassProblemDescriptor(totalDependencies).get();
    }

    @Override
    protected int getDefaultLimit() {
        return DEFAULT_COUPLING_LIMIT;
    }

    @Override
    protected String getConfigurationLabel() {
        return InspectionGadgetsLocalize.overlyCoupledClassClassCouplingLimitOption().get();
    }

    @Override
    public JComponent createOptionsPanel() {
        final String configurationLabel = getConfigurationLabel();
        final JLabel label = new JLabel(configurationLabel);
        final JFormattedTextField valueField = prepareNumberEditor(() -> m_limit, i -> m_limit = i);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets.right = UIUtil.DEFAULT_HGAP;
        final JPanel panel = new JPanel(new GridBagLayout());
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.insets.right = 0;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(valueField, constraints);

        final CheckBox arrayCheckBox = new CheckBox(
            InspectionGadgetsLocalize.includeJavaSystemClassesOption().get(),
            this,
            "m_includeJavaClasses"
        );
        final CheckBox objectCheckBox = new CheckBox(
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
        constraints.weighty = 1;
        constraints.gridwidth = 2;
        panel.add(objectCheckBox, constraints);
        return panel;
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ClassCouplingVisitor();
    }

    private class ClassCouplingVisitor extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // note: no call to super
            final int totalDependencies = calculateTotalDependencies(aClass);
            if (totalDependencies <= getLimit()) {
                return;
            }
            registerClassError(aClass, Integer.valueOf(totalDependencies));
        }

        private int calculateTotalDependencies(PsiClass aClass) {
            final CouplingVisitor visitor = new CouplingVisitor(aClass,
                m_includeJavaClasses, m_includeLibraryClasses
            );
            aClass.accept(visitor);
            return visitor.getNumDependencies();
        }
    }
}