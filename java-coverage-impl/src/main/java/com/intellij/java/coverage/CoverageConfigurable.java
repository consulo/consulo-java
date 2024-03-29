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

import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.execution.ExecutionBundle;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.coverage.CoverageRunner;
import consulo.language.editor.CodeInsightBundle;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.coverage.CoverageEnabledConfiguration;
import consulo.application.AllIcons;
import com.intellij.java.debugger.impl.classFilter.ClassFilterEditor;
import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.impl.util.JreVersionDetector;
import com.intellij.java.language.impl.codeInsight.PackageChooserDialog;
import com.intellij.java.language.impl.ui.PackageChooser;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.java.language.util.ClassFilter;
import consulo.component.extension.Extensions;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.awt.ListCellRendererWrapper;
import consulo.ui.ex.awt.VerticalFlowLayout;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.logging.Logger;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
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
 * @author ven
 */
public class CoverageConfigurable extends SettingsEditor<RunConfigurationBase> {
  private static final Logger LOG = Logger.getInstance(CoverageConfigurable.class);

  private final JreVersionDetector myVersionDetector = new JreVersionDetector();
  Project myProject;
  private MyClassFilterEditor myClassFilterEditor;
  private JLabel myCoverageNotSupportedLabel;
  private JComboBox myCoverageRunnerCb;
  private JPanel myRunnerPanel;
  private JCheckBox myTrackPerTestCoverageCb;
  private JCheckBox myTrackTestSourcesCb;

  private JRadioButton myTracingRb;
  private JRadioButton mySamplingRb;
  private final RunConfigurationBase myConfig;

  private static class MyClassFilterEditor extends ClassFilterEditor {
    public MyClassFilterEditor(Project project) {
      super(project, new ClassFilter() {
        public boolean isAccepted(PsiClass aClass) {
          if (aClass.getContainingClass() != null) return false;
          return true;
        }
      });
    }

    protected void addPatternFilter() {
      PackageChooser chooser = new PackageChooserDialog(CodeInsightBundle.message("coverage.pattern.filter.editor.choose.package.title"), myProject);
      chooser.show();
      if (chooser.isOK()) {
        List<PsiJavaPackage> packages = chooser.getSelectedPackages();
        if (!packages.isEmpty()) {
          for (final PsiJavaPackage aPackage : packages) {
            final String fqName = aPackage.getQualifiedName();
            final String pattern = fqName.length() > 0 ? fqName + ".*" : "*";
            myTableModel.addRow(createFilter(pattern));
          }
          int row = myTableModel.getRowCount() - 1;
          myTable.getSelectionModel().setSelectionInterval(row, row);
          myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));
          myTable.requestFocus();
        }
      }
    }

    protected String getAddPatternButtonText() {
      return CodeInsightBundle.message("coverage.button.add.package");
    }

    @Override
    protected Image getAddPatternButtonIcon() {
      return PlatformIconGroup.toolbardecoratorAddpackage();
    }
  }

  public CoverageConfigurable(RunConfigurationBase config) {
    myConfig = config;
    myProject = config.getProject();
  }

  protected void resetEditorFrom(final RunConfigurationBase runConfiguration) {
    final boolean isJre50;
    if (runConfiguration instanceof CommonJavaRunConfigurationParameters && myVersionDetector.isJre50Configured((CommonJavaRunConfigurationParameters)runConfiguration)) {
      isJre50 = true;
    } else if (runConfiguration instanceof ModuleBasedConfiguration){
      isJre50 = myVersionDetector.isModuleJre50Configured((ModuleBasedConfiguration)runConfiguration);
    } else {
      isJre50 = true;
    }

    myCoverageNotSupportedLabel.setVisible(!isJre50);

    final JavaCoverageEnabledConfiguration configuration = (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(runConfiguration);
    CoverageRunner runner = configuration.getCoverageRunner();
    if (runner != null) {
      myCoverageRunnerCb.setSelectedItem(new CoverageRunnerItem(runner));
    } else {
      final String runnerId = configuration.getRunnerId();
      if (runnerId != null){
        final CoverageRunnerItem runnerItem = new CoverageRunnerItem(runnerId);
        final DefaultComboBoxModel model = (DefaultComboBoxModel)myCoverageRunnerCb.getModel();
        if (model.getIndexOf(runnerItem) == -1) {
          model.addElement(runnerItem);
        }
        myCoverageRunnerCb.setSelectedItem(runnerItem);
      } else {
        myCoverageRunnerCb.setSelectedIndex(0);
      }
      runner = ((CoverageRunnerItem)myCoverageRunnerCb.getSelectedItem()).getRunner();
    }
    UIUtil.setEnabled(myRunnerPanel, isJre50, true);


    myClassFilterEditor.setFilters(configuration.getCoveragePatterns());
    final boolean isCoverageByTestApplicable = runner != null && runner.isCoverageByTestApplicable();
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

  protected void applyEditorTo(final RunConfigurationBase runConfiguration) throws ConfigurationException {
    final JavaCoverageEnabledConfiguration configuration = (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(runConfiguration);
    configuration.setCoveragePatterns(myClassFilterEditor.getFilters());
    configuration.setCoverageRunner(getSelectedRunner());
    configuration.setTrackPerTestCoverage(myTrackPerTestCoverageCb.isSelected());
    configuration.setSampling(mySamplingRb.isSelected());
    configuration.setTrackTestFolders(myTrackTestSourcesCb.isSelected());
  }

  @Nonnull
  protected JComponent createEditor() {
    JPanel result = new JPanel(new GridBagLayout());

    final DefaultComboBoxModel runnersModel = new DefaultComboBoxModel();
    myCoverageRunnerCb = new JComboBox(runnersModel);

    final JavaCoverageEnabledConfiguration javaCoverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(myConfig);
    LOG.assertTrue(javaCoverageEnabledConfiguration != null);
    final JavaCoverageEngine provider = javaCoverageEnabledConfiguration.getCoverageProvider();
    for (CoverageRunner runner : Extensions.getExtensions(CoverageRunner.EP_NAME)) {
      if (runner.acceptsCoverageEngine(provider)) {
        runnersModel.addElement(new CoverageRunnerItem(runner));
      }
    }
    myCoverageRunnerCb.setRenderer(new ListCellRendererWrapper<CoverageRunnerItem>() {
      @Override
      public void customize(JList list, CoverageRunnerItem value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(value.getPresentableName());
        }
      }
    });
    myCoverageRunnerCb.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final CoverageRunner runner = getSelectedRunner();
        enableTracingPanel(runner != null && runner.isCoverageByTestApplicable());
        myTrackPerTestCoverageCb.setEnabled(myTracingRb.isSelected() && canHavePerTestCoverage() && runner != null && runner.isCoverageByTestApplicable());
      }
    });
    myRunnerPanel = new JPanel(new GridBagLayout());
    myRunnerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    myRunnerPanel.add(new JLabel("Choose coverage runner:"), new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,10), 0, 0));
    myRunnerPanel.add(myCoverageRunnerCb, new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 0));
    final JPanel cPanel = new JPanel(new VerticalFlowLayout());

    mySamplingRb = new JRadioButton("Sampling");
    cPanel.add(mySamplingRb);
    myTracingRb = new JRadioButton("Tracing");
    cPanel.add(myTracingRb);

    final ButtonGroup group = new ButtonGroup();
    group.add(mySamplingRb);
    group.add(myTracingRb);

    ActionListener samplingListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final CoverageRunner runner = getSelectedRunner();
        myTrackPerTestCoverageCb.setEnabled(canHavePerTestCoverage() && myTracingRb.isSelected() && runner != null && runner.isCoverageByTestApplicable());
      }
    };

    mySamplingRb.addActionListener(samplingListener);
    myTracingRb.addActionListener(samplingListener);

    myTrackPerTestCoverageCb = new JCheckBox("Track per test coverage");
    final JPanel tracingPanel = new JPanel(new BorderLayout());
    tracingPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));
    tracingPanel.add(myTrackPerTestCoverageCb, BorderLayout.CENTER);
    cPanel.add(tracingPanel);
    myRunnerPanel.add(cPanel, new GridBagConstraints(0, 1, GridBagConstraints.REMAINDER, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 0));

    final GridBagConstraints gc = new GridBagConstraints(0, GridBagConstraints.RELATIVE,
                                                         1, 1, 1, 0,
                                                         GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                         new Insets(0, 0, 0, 0), 0, 0);
    result.add(myRunnerPanel, gc);

    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(IdeBorderFactory.createTitledBorder(ExecutionBundle.message("record.coverage.filters.title"), false));
    myClassFilterEditor = new MyClassFilterEditor(myProject);
    final GridBagConstraints bagConstraints =
      new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                             new Insets(0, 0, 0, 0), 0, 0);
    panel.add(myClassFilterEditor, bagConstraints);

    bagConstraints.weighty = 0;
    myTrackTestSourcesCb = new JCheckBox("Enable coverage in test folders");
    panel.add(myTrackTestSourcesCb, bagConstraints);

    result.add(panel, gc);

    myCoverageNotSupportedLabel = new JLabel(CodeInsightBundle.message("code.coverage.is.not.supported"));
    myCoverageNotSupportedLabel.setIcon(TargetAWT.to(AllIcons.General.WarningDialog));
    result.add(myCoverageNotSupportedLabel, gc);
    return result;
  }

  @Nullable
  private CoverageRunner getSelectedRunner() {
    final CoverageRunnerItem runnerItem = (CoverageRunnerItem)myCoverageRunnerCb.getSelectedItem();
    if (runnerItem == null) {
      LOG.debug("Available runners: " + myCoverageRunnerCb.getModel().getSize());
    }
    return runnerItem != null ? runnerItem.getRunner() : null;
  }

  private void enableTracingPanel(final boolean enabled) {
    myTracingRb.setEnabled(enabled);
    if (!enabled) {
      mySamplingRb.setSelected(true);
    }
  }

  private static class CoverageRunnerItem {
    private CoverageRunner myRunner;
    private @Nonnull
	String myRunnerId;

    private CoverageRunnerItem(@Nonnull CoverageRunner runner) {
      myRunner = runner;
      myRunnerId = runner.getId();
    }

    private CoverageRunnerItem(String runnerId) {
      myRunnerId = runnerId;
    }

    public CoverageRunner getRunner() {
      return myRunner;
    }

    public String getRunnerId() {
      return myRunnerId;
    }

    public String getPresentableName() {
      return myRunner != null ? myRunner.getPresentableName() : myRunnerId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CoverageRunnerItem that = (CoverageRunnerItem)o;

      if (!myRunnerId.equals(that.myRunnerId)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myRunnerId.hashCode();
    }
  }
}
