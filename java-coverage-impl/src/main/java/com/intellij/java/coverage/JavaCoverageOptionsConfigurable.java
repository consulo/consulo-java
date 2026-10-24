/*
 * Copyright 2013-2026 consulo.io
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

import consulo.application.Application;
import consulo.configurable.Configurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.execution.coverage.CoverageEngine;
import consulo.execution.coverage.CoverageRunner;
import consulo.java.coverage.localize.JavaCoverageLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.FormBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * The Java part of the project coverage settings.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
public class JavaCoverageOptionsConfigurable extends SimpleConfigurableByProperties implements Configurable {
    private final JavaCoverageOptionsProvider myProvider;

    public JavaCoverageOptionsConfigurable(JavaCoverageOptionsProvider provider) {
        myProvider = provider;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return JavaCoverageLocalize.settingsCoverageJavaJavaCoverage();
    }

    @RequiredUIAccess
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
        JavaCoverageEngine engine = Application.get().getExtensionPoint(CoverageEngine.class).findExtensionOrFail(JavaCoverageEngine.class);
        List<CoverageRunner> runners = new ArrayList<>();
        Application.get().getExtensionPoint(CoverageRunner.class).forEach(runner -> {
            if (runner.acceptsCoverageEngine(engine)) {
                runners.add(runner);
            }
        });

        ComboBox<CoverageRunner> runnerBox = ComboBox.create(runners);
        runnerBox.setTextRenderer(runner -> runner == null ? LocalizeValue.empty() : LocalizeValue.of(runner.getPresentableName()));
        propertyBuilder.add(runnerBox, myProvider::getCoverageRunner, myProvider::setCoverageRunner);

        CheckBox branchCoverage = CheckBox.create(JavaCoverageLocalize.runConfigurationCoverageBranches());
        propertyBuilder.add(branchCoverage, myProvider::isBranchCoverage, myProvider::setBranchCoverage);

        CheckBox testTracking = CheckBox.create(JavaCoverageLocalize.runConfigurationTrackPerTestCoverage());
        propertyBuilder.add(testTracking, myProvider::isTestTracking, myProvider::setTestTracking);

        CheckBox testModulesCoverage = CheckBox.create(JavaCoverageLocalize.runConfigurationEnableCoverageInTestFolders());
        propertyBuilder.add(testModulesCoverage, myProvider::isTestModulesCoverage, myProvider::setTestModulesCoverage);

        Runnable updateEnabled = () -> {
            CoverageRunner runner = runnerBox.getValue();
            boolean byTest = runner != null && runner.isCoverageByTestApplicable();
            branchCoverage.setEnabled(byTest);
            testTracking.setEnabled(byTest && branchCoverage.getValueOrError());
        };
        runnerBox.addValueListener(event -> updateEnabled.run());
        branchCoverage.addValueListener(event -> updateEnabled.run());

        FormBuilder builder = FormBuilder.create();
        builder.addLabeled(JavaCoverageLocalize.runConfigurationChooseCoverageRunner(), runnerBox);

        VerticalLayout layout = VerticalLayout.create();
        layout.add(builder.build());
        layout.add(branchCoverage);
        layout.add(testTracking);
        layout.add(testModulesCoverage);
        return layout;
    }
}
