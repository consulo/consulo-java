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

import com.intellij.java.debugger.impl.classFilter.UnifiedClassFilterEditor;
import com.intellij.java.language.impl.ui.PackageChooser;
import com.intellij.java.language.impl.ui.PackageChooserFactory;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.coverage.CoverageEnabledConfiguration;
import consulo.execution.coverage.CoverageRunner;
import consulo.java.coverage.localize.JavaCoverageLocalize;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.RadioButton;
import consulo.ui.RadioGroup;
import consulo.ui.Space;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.ui.util.FormBuilder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
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

    private final Project myProject;
    private final RunConfigurationBase myConfig;

    private @Nullable CoverageForm myForm;

    public CoverageConfigurable(RunConfigurationBase config) {
        myConfig = config;
        myProject = config.getProject();
    }

    @Override
    @RequiredUIAccess
    protected Component createUIComponent() {
        CoverageForm form = new CoverageForm();
        myForm = form;
        return form.myComponent;
    }

    @Override
    @RequiredUIAccess
    protected void resetEditorFrom(RunConfigurationBase runConfiguration) {
        CoverageForm form = myForm;
        if (form != null) {
            form.reset(runConfiguration);
        }
    }

    @Override
    @RequiredUIAccess
    protected void applyEditorTo(RunConfigurationBase runConfiguration) throws ConfigurationException {
        CoverageForm form = myForm;
        if (form != null) {
            form.apply(runConfiguration);
        }
    }

    protected boolean canHavePerTestCoverage() {
        return CoverageEnabledConfiguration.getOrCreate(myConfig).canHavePerTestCoverage();
    }

    private static class CoverageClassFilterEditor extends UnifiedClassFilterEditor {
        @RequiredUIAccess
        private CoverageClassFilterEditor(Project project) {
            super(project, aClass -> aClass.getContainingClass() == null);
        }

        @Override
        protected LocalizeValue getAddPatternButtonText() {
            return JavaCoverageLocalize.coverageButtonAddPackage();
        }

        @Override
        protected Image getAddPatternButtonIcon() {
            return PlatformIconGroup.nodesPackage();
        }

        @Override
        protected boolean addPatternButtonVisible() {
            return true;
        }

        @Override
        @RequiredUIAccess
        protected void addPatternFilter() {
            PackageChooser packageChooser = myProject.getInstance(PackageChooserFactory.class).create();
            List<PsiJavaPackage> packages = packageChooser.showAndSelect();
            if (packages != null) {
                for (PsiJavaPackage aPackage : packages) {
                    String fqName = aPackage.getQualifiedName();
                    addPattern(fqName.isEmpty() ? "*" : fqName + ".*");
                }
            }
        }
    }

    private class CoverageForm {
        private final MutableFlatDataModel<CoverageRunnerItem> myRunnersModel;
        private final ComboBox<CoverageRunnerItem> myCoverageRunnerCb;
        private final RadioGroup<Boolean> mySamplingGroup;
        private final RadioButton myTracingRb;
        private final CheckBox myTrackPerTestCoverageCb;
        private final CoverageClassFilterEditor myClassFilterEditor;
        private final CheckBox myTrackTestSourcesCb;
        private final Component myComponent;

        @RequiredUIAccess
        private CoverageForm() {
            List<CoverageRunnerItem> runners = new ArrayList<>();
            JavaCoverageEnabledConfiguration javaCoverageEnabledConfiguration = JavaCoverageEnabledConfiguration.getFrom(myConfig);
            LOG.assertTrue(javaCoverageEnabledConfiguration != null);
            JavaCoverageEngine provider = javaCoverageEnabledConfiguration.getCoverageProvider();
            myProject.getApplication().getExtensionPoint(CoverageRunner.class).forEach(runner -> {
                if (runner.acceptsCoverageEngine(provider)) {
                    runners.add(new CoverageRunnerItem(runner));
                }
            });

            myRunnersModel = FlatDataModel.of(runners);
            myCoverageRunnerCb = ComboBox.create(myRunnersModel);
            myCoverageRunnerCb.setTextRenderer(item -> item == null ? LocalizeValue.empty() : LocalizeValue.of(item.getPresentableName()));

            mySamplingGroup = RadioGroup.create();
            RadioButton samplingRb = mySamplingGroup.newButton(JavaCoverageLocalize.runConfigurationCoverageSampling(), Boolean.TRUE);
            myTracingRb = mySamplingGroup.newButton(JavaCoverageLocalize.runConfigurationCoverageTracing(), Boolean.FALSE);

            myTrackPerTestCoverageCb = CheckBox.create(JavaCoverageLocalize.runConfigurationTrackPerTestCoverage());
            myTrackPerTestCoverageCb.paddingBuilder().leftSet(Space.XX_LARGE).apply();

            myClassFilterEditor = new CoverageClassFilterEditor(myProject);
            myTrackTestSourcesCb = CheckBox.create(JavaCoverageLocalize.runConfigurationEnableCoverageInTestFolders());

            myCoverageRunnerCb.addValueListener(event -> {
                CoverageRunner runner = getSelectedRunner();
                enableTracingPanel(runner != null && runner.isCoverageByTestApplicable());
                updateTrackPerTestCoverage();
            });
            mySamplingGroup.addValueListener(value -> updateTrackPerTestCoverage());

            FormBuilder runnerBuilder = FormBuilder.create();
            runnerBuilder.addLabeled(JavaCoverageLocalize.runConfigurationChooseCoverageRunner(), myCoverageRunnerCb);

            VerticalLayout runnerPanel = VerticalLayout.create();
            runnerPanel.add(runnerBuilder.build());
            runnerPanel.add(samplingRb);
            runnerPanel.add(myTracingRb);
            runnerPanel.add(myTrackPerTestCoverageCb);

            DockLayout filtersPanel = DockLayout.create();
            filtersPanel.center(myClassFilterEditor.getComponent());
            filtersPanel.bottom(myTrackTestSourcesCb);

            DockLayout root = DockLayout.create();
            root.top(runnerPanel);
            root.center(LabeledLayout.create(JavaCoverageLocalize.recordCoverageFiltersTitle(), filtersPanel));
            myComponent = root;
        }

        private @Nullable CoverageRunner getSelectedRunner() {
            CoverageRunnerItem runnerItem = myCoverageRunnerCb.getValue();
            if (runnerItem == null) {
                LOG.debug("Available runners: " + myRunnersModel.getSize());
            }
            return runnerItem != null ? runnerItem.getRunner() : null;
        }

        private boolean isTracingSelected() {
            return Boolean.FALSE.equals(mySamplingGroup.getValue());
        }

        @RequiredUIAccess
        private void enableTracingPanel(boolean enabled) {
            myTracingRb.setEnabled(enabled);
            if (!enabled) {
                mySamplingGroup.setValue(Boolean.TRUE, false);
            }
        }

        @RequiredUIAccess
        private void updateTrackPerTestCoverage() {
            CoverageRunner runner = getSelectedRunner();
            myTrackPerTestCoverageCb.setEnabled(
                isTracingSelected() && canHavePerTestCoverage() && runner != null && runner.isCoverageByTestApplicable()
            );
        }

        @RequiredUIAccess
        private void selectRunnerItem(CoverageRunnerItem runnerItem) {
            int index = myRunnersModel.indexOf(runnerItem);
            if (index == -1) {
                myRunnersModel.add(runnerItem);
                myCoverageRunnerCb.setValue(runnerItem, false);
            }
            else {
                myCoverageRunnerCb.setValue(myRunnersModel.get(index), false);
            }
        }

        @RequiredUIAccess
        private void reset(RunConfigurationBase runConfiguration) {
            JavaCoverageEnabledConfiguration configuration =
                (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(runConfiguration);

            CoverageRunner runner = configuration.getCoverageRunner();
            if (runner != null) {
                selectRunnerItem(new CoverageRunnerItem(runner));
            }
            else {
                String runnerId = configuration.getRunnerId();
                if (runnerId != null) {
                    selectRunnerItem(new CoverageRunnerItem(runnerId));
                }
                else {
                    myCoverageRunnerCb.selectFirst();
                }
                runner = getSelectedRunner();
            }

            myClassFilterEditor.setFilters(configuration.getCoveragePatterns());

            boolean isCoverageByTestApplicable = runner != null && runner.isCoverageByTestApplicable();
            myTracingRb.setEnabled(isCoverageByTestApplicable);
            mySamplingGroup.setValue(configuration.isSampling() || !isCoverageByTestApplicable, false);
            myTrackPerTestCoverageCb.setValue(configuration.isTrackPerTestCoverage());
            myTrackPerTestCoverageCb.setEnabled(isCoverageByTestApplicable && isTracingSelected() && canHavePerTestCoverage());
            myTrackTestSourcesCb.setValue(configuration.isTrackTestFolders());
        }

        @RequiredUIAccess
        private void apply(RunConfigurationBase runConfiguration) {
            JavaCoverageEnabledConfiguration configuration =
                (JavaCoverageEnabledConfiguration)CoverageEnabledConfiguration.getOrCreate(runConfiguration);
            configuration.setCoveragePatterns(myClassFilterEditor.getFilters());
            configuration.setCoverageRunner(getSelectedRunner());
            configuration.setTrackPerTestCoverage(myTrackPerTestCoverageCb.getValueOrError());
            configuration.setSampling(!isTracingSelected());
            configuration.setTrackTestFolders(myTrackTestSourcesCb.getValueOrError());
        }
    }

    private static class CoverageRunnerItem {
        private CoverageRunner myRunner;
        private String myRunnerId;

        private CoverageRunnerItem(CoverageRunner runner) {
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
