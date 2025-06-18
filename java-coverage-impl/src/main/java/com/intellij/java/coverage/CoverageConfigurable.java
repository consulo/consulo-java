/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.java.coverage;

import com.intellij.java.debugger.impl.classFilter.ClassFilterEditor;
import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.impl.util.JreVersionDetector;
import com.intellij.java.language.impl.ui.PackageChooser;
import com.intellij.java.language.impl.ui.PackageChooserFactory;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.coverage.CoverageEnabledConfiguration;
import consulo.execution.coverage.CoverageRunner;
import consulo.execution.localize.ExecutionLocalize;
import consulo.java.coverage.localize.JavaCoverageLocalize;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Base {@link Configurable} for configuring code coverage
 * To obtain a full configurable use
 * <code>
 * SettingsEditorGroup<YourConfiguration> group = new SettingsEditorGroup<YourConfiguration>();
 * group.addEditor(title, yourConfigurable);
 * group.addEditor(title, yourCoverageConfigurable);
 * </code>
 *
 * @author ven
 */
public class CoverageConfigurable extends SettingsEditor<RunConfigurationBase> {
    private static final Logger LOG = Logger.getInstance(CoverageConfigurable.class);

    private final JreVersionDetector myVersionDetector = new JreVersionDetector();
    private final Project myProject;
    private MyClassFilterEditor myClassFilterEditor;
    private JLabel myCoverageNotSupportedLabel;
    private JComboBox<CoverageRunnerItem> myCoverageRunnerCb;
    private JPanel myRunnerPanel;
    private JCheckBox myTrackPerTestCoverageCb;
    private JCheckBox myTrackTestSourcesCb;

    private JRadioButton myTracingRb;
    private JRadioButton mySamplingRb;
    private final RunConfigurationBase myConfig;

    private static class MyClassFilterEditor extends ClassFilterEditor {
        public MyClassFilterEditor(Project project) {
            super(project, aClass -> aClass.getContainingClass() == null);
        }

        @Override
        protected void addPatternFilter() {
            PackageChooser packageChooser = myProject.getInstance(PackageChooserFactory.class).create();

            List<PsiJavaPackage> packages = packageChooser.showAndSelect();
            if (packages != null) {
                if (!packages.isEmpty()) {
                    for (PsiJavaPackage aPackage : packages) {
                        String fqName = aPackage.getQualifiedName();
                        String pattern = fqName.isEmpty() ? "*" : fqName + ".*";
                        myTableModel.addRow(createFilter(pattern));
                    }
                    int row = myTableModel.getRowCount() - 1;
                    myTable.getSelectionModel().setSelectionInterval(row, row);
                    myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));
                    myTable.requestFocus();
                }
            }
        }

        @Override
        protected String getAddPatternButtonText() {
            return CodeInsightLocalize.coverageButtonAddPackage().get();
        }

        @Override
        protected Image getAddPatternButtonIcon() {
            return PlatformIconGroup.nodesPackage();
        }
    }

    public CoverageConfigurable(RunConfigurationBase config) {
        myConfig = config;
        myProject = config.getProject();
    }

    @Override
    protected void resetEditorFrom(RunConfigurationBase runConfiguration) {
        boolean isJre50;
        if (runConfiguration instanceof CommonJavaRunConfigurationParameters configurationParameters
            && myVersionDetector.isJre50Configured(configurationParameters)) {
            isJre50 = true;
        }
        else if (runConfiguration instanceof ModuleBasedConfiguration moduleBasedConfiguration) {
            isJre50 = myVersionDetector.isModuleJre50Configured(moduleBasedConfiguration);
        }
        else {
            isJre50 = true;
        }

        myCoverageNotSupportedLabel.setVisible(!isJre50);

        JavaCoverageEnabledConfiguration configuration =
            (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(runConfiguration);
        CoverageRunner runner = configuration.getCoverageRunner();
        if (runner != null) {
            myCoverageRunnerCb.setSelectedItem(new CoverageRunnerItem(runner));
        }
        else {
            String runnerId = configuration.getRunnerId();
            if (runnerId != null) {
                CoverageRunnerItem runnerItem = new CoverageRunnerItem(runnerId);
                @SuppressWarnings("unchecked")
                DefaultComboBoxModel<CoverageRunnerItem> model = (DefaultComboBoxModel)myCoverageRunnerCb.getModel();
                if (model.getIndexOf(runnerItem) == -1) {
                    model.addElement(runnerItem);
                }
                myCoverageRunnerCb.setSelectedItem(runnerItem);
            }
            else {
                myCoverageRunnerCb.setSelectedIndex(0);
            }
            runner = ((CoverageRunnerItem)myCoverageRunnerCb.getSelectedItem()).getRunner();
        }
        UIUtil.setEnabled(myRunnerPanel, isJre50, true);


        myClassFilterEditor.setFilters(configuration.getCoveragePatterns());
        boolean isCoverageByTestApplicable = runner != null && runner.isCoverageByTestApplicable();
        myTracingRb.setEnabled(myTracingRb.isEnabled() && isCoverageByTestApplicable);
        mySamplingRb.setSelected(configuration.isSampling() || !isCoverageByTestApplicable);
        myTracingRb.setSelected(!mySamplingRb.isSelected());

        myTrackPerTestCoverageCb.setSelected(configuration.isTrackPerTestCoverage());
        myTrackPerTestCoverageCb.setEnabled(myTracingRb.isEnabled() && myTracingRb.isSelected() && canHavePerTestCoverage());

        myTrackTestSourcesCb.setSelected(configuration.isTrackTestFolders());
    }

    protected boolean canHavePerTestCoverage() {
        return CoverageEnabledConfiguration.getOrCreate(myConfig).canHavePerTestCoverage();
    }

    @Override
    protected void applyEditorTo(RunConfigurationBase runConfiguration) throws ConfigurationException {
        JavaCoverageEnabledConfiguration configuration =
            (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(runConfiguration);
        configuration.setCoveragePatterns(myClassFilterEditor.getFilters());
        configuration.setCoverageRunner(getSelectedRunner());
        configuration.setTrackPerTestCoverage(myTrackPerTestCoverageCb.isSelected());
        configuration.setSampling(mySamplingRb.isSelected());
        configuration.setTrackTestFolders(myTrackTestSourcesCb.isSelected());
    }

    @Nonnull
    @Override
    protected JComponent createEditor() {
        JPanel result = new JPanel(new GridBagLayout());

        DefaultComboBoxModel<CoverageRunnerItem> runnersModel = new DefaultComboBoxModel<>();
        myCoverageRunnerCb = new JComboBox<>(runnersModel);

        JavaCoverageEnabledConfiguration javaCoverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(myConfig);
        LOG.assertTrue(javaCoverageEnabledConfiguration != null);
        JavaCoverageEngine provider = javaCoverageEnabledConfiguration.getCoverageProvider();
        myProject.getApplication().getExtensionPoint(CoverageRunner.class).forEach(runner -> {
            if (runner.acceptsCoverageEngine(provider)) {
                runnersModel.addElement(new CoverageRunnerItem(runner));
            }
        });
        myCoverageRunnerCb.setRenderer(new ListCellRendererWrapper<>() {
            @Override
            public void customize(JList list, CoverageRunnerItem value, int index, boolean selected, boolean hasFocus) {
                if (value != null) {
                    setText(value.getPresentableName());
                }
            }
        });
        myCoverageRunnerCb.addActionListener(e -> {
            CoverageRunner runner = getSelectedRunner();
            enableTracingPanel(runner != null && runner.isCoverageByTestApplicable());
            myTrackPerTestCoverageCb.setEnabled(
                myTracingRb.isSelected() && canHavePerTestCoverage() && runner != null && runner.isCoverageByTestApplicable()
            );
        });
        myRunnerPanel = new JPanel(new GridBagLayout());
        myRunnerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        myRunnerPanel.add(
            new JLabel(JavaCoverageLocalize.runConfigurationChooseCoverageRunner().get()),
            new GridBagConstraints(
                0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE,
                JBUI.insetsRight(10), 0, 0
            )
        );
        myRunnerPanel.add(
            myCoverageRunnerCb,
            new GridBagConstraints(
                1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE,
                JBUI.emptyInsets(), 0, 0
            )
        );
        JPanel cPanel = new JPanel(new VerticalFlowLayout());

        mySamplingRb = new JRadioButton(LocalizeValue.localizeTODO("Sampling").get());
        cPanel.add(mySamplingRb);
        myTracingRb = new JRadioButton(LocalizeValue.localizeTODO("Tracing").get());
        cPanel.add(myTracingRb);

        ButtonGroup group = new ButtonGroup();
        group.add(mySamplingRb);
        group.add(myTracingRb);

        ActionListener samplingListener = e -> {
            CoverageRunner runner = getSelectedRunner();
            myTrackPerTestCoverageCb.setEnabled(
                canHavePerTestCoverage() && myTracingRb.isSelected() && runner != null && runner.isCoverageByTestApplicable()
            );
        };

        mySamplingRb.addActionListener(samplingListener);
        myTracingRb.addActionListener(samplingListener);

        myTrackPerTestCoverageCb = new JCheckBox(JavaCoverageLocalize.runConfigurationTrackPerTestCoverage().get());
        JPanel tracingPanel = new JPanel(new BorderLayout());
        tracingPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));
        tracingPanel.add(myTrackPerTestCoverageCb, BorderLayout.CENTER);
        cPanel.add(tracingPanel);
        myRunnerPanel.add(
            cPanel,
            new GridBagConstraints(
                0, 1, GridBagConstraints.REMAINDER, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE,
                JBUI.emptyInsets(), 0, 0
            )
        );

        GridBagConstraints gc = new GridBagConstraints(
            0, GridBagConstraints.RELATIVE, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
            JBUI.emptyInsets(), 0, 0
        );
        result.add(myRunnerPanel, gc);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(IdeBorderFactory.createTitledBorder(ExecutionLocalize.recordCoverageFiltersTitle().get(), false));
        myClassFilterEditor = new MyClassFilterEditor(myProject);
        GridBagConstraints bagConstraints = new GridBagConstraints(
            0, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
            JBUI.emptyInsets(), 0, 0
        );
        panel.add(myClassFilterEditor, bagConstraints);

        bagConstraints.weighty = 0;
        myTrackTestSourcesCb = new JCheckBox(JavaCoverageLocalize.runConfigurationEnableCoverageInTestFolders().get());
        panel.add(myTrackTestSourcesCb, bagConstraints);

        result.add(panel, gc);

        myCoverageNotSupportedLabel = new JLabel(CodeInsightLocalize.codeCoverageIsNotSupported().get());
        myCoverageNotSupportedLabel.setIcon(TargetAWT.to(PlatformIconGroup.generalWarningdialog()));
        result.add(myCoverageNotSupportedLabel, gc);
        return result;
    }

    @Nullable
    private CoverageRunner getSelectedRunner() {
        CoverageRunnerItem runnerItem = (CoverageRunnerItem)myCoverageRunnerCb.getSelectedItem();
        if (runnerItem == null) {
            LOG.debug("Available runners: " + myCoverageRunnerCb.getModel().getSize());
        }
        return runnerItem != null ? runnerItem.getRunner() : null;
    }

    private void enableTracingPanel(boolean enabled) {
        myTracingRb.setEnabled(enabled);
        if (!enabled) {
            mySamplingRb.setSelected(true);
        }
    }

    private static class CoverageRunnerItem {
        private CoverageRunner myRunner;
        @Nonnull
        private String myRunnerId;

        private CoverageRunnerItem(@Nonnull CoverageRunner runner) {
            myRunner = runner;
            myRunnerId = runner.getId();
        }

        private CoverageRunnerItem(@Nonnull String runnerId) {
            myRunnerId = runnerId;
        }

        public CoverageRunner getRunner() {
            return myRunner;
        }

        @Nonnull
        public String getRunnerId() {
            return myRunnerId;
        }

        public String getPresentableName() {
            return myRunner != null ? myRunner.getPresentableName() : myRunnerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CoverageRunnerItem that = (CoverageRunnerItem)o;

            return myRunnerId.equals(that.myRunnerId);
        }

        @Override
        public int hashCode() {
            return myRunnerId.hashCode();
        }
    }
}
