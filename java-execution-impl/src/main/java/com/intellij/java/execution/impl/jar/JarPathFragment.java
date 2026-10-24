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
package com.intellij.java.execution.impl.jar;

import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;

/**
 * The JAR a {@link JarApplicationConfiguration} runs.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class JarPathFragment implements RunConfigurationFragment<JarApplicationConfiguration> {
    @Override
    public String getId() {
        return "jarPath";
    }

    @Override
    public LocalizeValue getName() {
        return JavaExecutionLocalize.jarApplicationConfigurationFragmentJarPath();
    }

    @Override
    public boolean isRemovable() {
        return false;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<JarApplicationConfiguration> createEditor(Project project, Disposable parent) {
        FileChooserTextBoxBuilder builder = FileChooserTextBoxBuilder.create(project);
        builder.fileChooserDescriptor(new FileChooserDescriptor(false, false, true, true, false, false));
        builder.dialogTitle(JavaExecutionLocalize.jarApplicationConfigurationChooseJarTitle());
        builder.uiDisposable(parent);
        FileChooserTextBoxBuilder.Controller jarPath = builder.build();

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(JavaExecutionLocalize.jarApplicationConfigurationJarPathLabel(), jarPath.getComponent());
            }

            @RequiredUIAccess
            @Override
            public void reset(JarApplicationConfiguration target) {
                jarPath.setValue(FileUtil.toSystemDependentName(target.getJarPath()), false);
            }

            @RequiredUIAccess
            @Override
            public void apply(JarApplicationConfiguration target) {
                target.setJarPath(FileUtil.toSystemIndependentName(StringUtil.notNullize(jarPath.getValue()).trim()));
            }
        };
    }
}
