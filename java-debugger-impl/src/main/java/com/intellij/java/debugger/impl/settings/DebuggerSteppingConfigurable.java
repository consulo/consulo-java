/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.settings;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.impl.classFilter.ClassFilterEditor;
import com.intellij.java.debugger.impl.ui.JavaDebuggerSupport;
import consulo.configurable.IdeaConfigurableUi;
import consulo.disposer.Disposable;
import consulo.ui.ex.awt.JBUI;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

class DebuggerSteppingConfigurable implements IdeaConfigurableUi<DebuggerSettings> {
    private JCheckBox myCbStepInfoFiltersEnabled;
    private JCheckBox myCbSkipSyntheticMethods;
    private JCheckBox myCbSkipConstructors;
    private JCheckBox myCbSkipClassLoaders;
    private ClassFilterEditor mySteppingFilterEditor;
    private JCheckBox myCbSkipSimpleGetters;

    @Override
    public void reset(@Nonnull DebuggerSettings settings) {
        myCbSkipSimpleGetters.setSelected(settings.SKIP_GETTERS);
        myCbSkipSyntheticMethods.setSelected(settings.SKIP_SYNTHETIC_METHODS);
        myCbSkipConstructors.setSelected(settings.SKIP_CONSTRUCTORS);
        myCbSkipClassLoaders.setSelected(settings.SKIP_CLASSLOADERS);

        myCbStepInfoFiltersEnabled.setSelected(settings.TRACING_FILTERS_ENABLED);

        mySteppingFilterEditor.setFilters(settings.getSteppingFilters());
        mySteppingFilterEditor.setEnabled(settings.TRACING_FILTERS_ENABLED);
    }

    @Override
    public void apply(@Nonnull DebuggerSettings settings) {
        getSettingsTo(settings);
    }

    private void getSettingsTo(DebuggerSettings settings) {
        settings.SKIP_GETTERS = myCbSkipSimpleGetters.isSelected();
        settings.SKIP_SYNTHETIC_METHODS = myCbSkipSyntheticMethods.isSelected();
        settings.SKIP_CONSTRUCTORS = myCbSkipConstructors.isSelected();
        settings.SKIP_CLASSLOADERS = myCbSkipClassLoaders.isSelected();
        settings.TRACING_FILTERS_ENABLED = myCbStepInfoFiltersEnabled.isSelected();

        mySteppingFilterEditor.stopEditing();
        settings.setSteppingFilters(mySteppingFilterEditor.getFilters());
    }

    @Override
    public boolean isModified(@Nonnull DebuggerSettings currentSettings) {
        DebuggerSettings debuggerSettings = currentSettings.clone();
        getSettingsTo(debuggerSettings);
        return !debuggerSettings.equals(currentSettings);
    }

    @Override
    @Nonnull
    public JComponent getComponent(@Nonnull Disposable parentDisposable) {
        final JPanel panel = new JPanel(new GridBagLayout());
        myCbSkipSyntheticMethods = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.synthetic.methods"));
        myCbSkipConstructors = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.constructors"));
        myCbSkipClassLoaders = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.classLoaders"));
        myCbSkipSimpleGetters = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.simple.getters"));
        myCbStepInfoFiltersEnabled = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.step.filters.list.header"));
        panel.add(myCbSkipSyntheticMethods, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0));
        panel.add(myCbSkipConstructors, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0));
        panel.add(myCbSkipClassLoaders, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0));
        panel.add(myCbSkipSimpleGetters, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0));
        panel.add(myCbStepInfoFiltersEnabled, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
            GridBagConstraints.NONE, JBUI.insetsTop(8), 0, 0));

        mySteppingFilterEditor = new ClassFilterEditor(JavaDebuggerSupport.getContextProjectForEditorFieldsInDebuggerConfigurables(), null,
            "reference.viewBreakpoints.classFilters.newPattern");
        panel.add(mySteppingFilterEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER,
            GridBagConstraints.BOTH, JBUI.insetsLeft(5), 0, 0));

        myCbStepInfoFiltersEnabled.addActionListener(e -> mySteppingFilterEditor.setEnabled(myCbStepInfoFiltersEnabled.isSelected()));
        return panel;
    }
}