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
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.siyeh.ig.BaseInspection;
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
public class MethodCountInspection extends BaseInspection {
    private static final int DEFAULT_METHOD_COUNT_LIMIT = 20;

    @SuppressWarnings({"PublicField"})
    public int m_limit = DEFAULT_METHOD_COUNT_LIMIT;

    @SuppressWarnings({"PublicField"})
    public boolean ignoreGettersAndSetters = false;

    @Nonnull
    @Override
    @Pattern(VALID_ID_PATTERN)
    public String getID() {
        return "ClassWithTooManyMethods";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return InspectionGadgetsLocalize.tooManyMethodsDisplayName();
    }

    @Override
    public JComponent createOptionsPanel() {
        final JComponent panel = new JPanel(new GridBagLayout());
        final Component label = new JLabel(InspectionGadgetsLocalize.methodCountLimitOption().get());
        final JFormattedTextField valueField = prepareNumberEditor(() -> m_limit, i -> m_limit = i);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets.right = UIUtil.DEFAULT_HGAP;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.insets.right = 0;
        panel.add(valueField, constraints);

        final CheckBox gettersSettersCheckBox = new CheckBox(
            InspectionGadgetsLocalize.methodCountIgnoreGettersSettersOption().get(),
            this,
            "ignoreGettersAndSetters"
        );

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weighty = 1.0;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        panel.add(gettersSettersCheckBox, constraints);

        return panel;
    }


    @Override
    @Nonnull
    public String buildErrorString(Object... infos) {
        final Integer count = (Integer) infos[0];
        return InspectionGadgetsLocalize.tooManyMethodsProblemDescriptor(count).get();
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MethodCountVisitor();
    }

    private class MethodCountVisitor extends BaseInspectionVisitor {

        @Override
        public void visitClass(@Nonnull PsiClass aClass) {
            // note: no call to super
            final int methodCount = calculateTotalMethodCount(aClass);
            if (methodCount <= m_limit) {
                return;
            }
            registerClassError(aClass, Integer.valueOf(methodCount));
        }

        private int calculateTotalMethodCount(PsiClass aClass) {
            final PsiMethod[] methods = aClass.getMethods();
            int totalCount = 0;
            for (final PsiMethod method : methods) {
                if (method.isConstructor()) {
                    continue;
                }
                if (ignoreGettersAndSetters) {
                    if (PropertyUtil.isSimpleGetter(method) ||
                        PropertyUtil.isSimpleSetter(method)) {
                        continue;
                    }
                }
                totalCount++;
            }
            return totalCount;
        }
    }
}