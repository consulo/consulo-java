/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.java.execution.impl.ui.CommonJavaParametersLayout;
import com.intellij.java.execution.impl.ui.UnifiedConfigurationModuleSelector;
import com.intellij.java.execution.impl.ui.UnifiedJrePathEditor;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.util.FormBuilder;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public class JarApplicationConfigurable extends SettingsEditor<JarApplicationConfiguration> {
    private final Project myProject;

    private @Nullable JarApplicationParametersLayout myLayout;

    public JarApplicationConfigurable(Project project) {
        myProject = project;
    }

    @Override
    @RequiredUIAccess
    protected Component createUIComponent() {
        JarApplicationParametersLayout layout = new JarApplicationParametersLayout();
        layout.build();
        myLayout = layout;
        return layout.getComponent();
    }

    @Override
    @RequiredUIAccess
    protected void resetEditorFrom(JarApplicationConfiguration configuration) {
        JarApplicationParametersLayout layout = myLayout;
        if (layout != null) {
            layout.reset(configuration);
        }
    }

    @Override
    @RequiredUIAccess
    protected void applyEditorTo(JarApplicationConfiguration configuration) throws ConfigurationException {
        JarApplicationParametersLayout layout = myLayout;
        if (layout != null) {
            layout.apply(configuration);
        }
    }

    private class JarApplicationParametersLayout extends CommonJavaParametersLayout<JarApplicationConfiguration> {
        private final FileChooserTextBoxBuilder.Controller myJarPathBox;
        private final UnifiedJrePathEditor myJrePathEditor;
        private final UnifiedConfigurationModuleSelector myModuleSelector;

        @RequiredUIAccess
        private JarApplicationParametersLayout() {
            super(myProject.getApplication().getInstance(DialogService.class));

            FileChooserTextBoxBuilder jarPathBuilder = FileChooserTextBoxBuilder.create(myProject);
            jarPathBuilder.fileChooserDescriptor(new FileChooserDescriptor(false, false, true, true, false, false));
            jarPathBuilder.dialogTitle(JavaExecutionLocalize.jarApplicationConfigurationChooseJarTitle());
            myJarPathBox = jarPathBuilder.build();

            myJrePathEditor = new UnifiedJrePathEditor(JarApplicationConfigurable.this);

            myModuleSelector = new UnifiedConfigurationModuleSelector(myProject, JavaExecutionLocalize.runConfigurationModuleWholeProject()) {
                @Override
                public boolean isModuleAccepted(Module module) {
                    return true;
                }
            };
        }

        @Override
        @RequiredUIAccess
        protected void addBefore(FormBuilder builder) {
            builder.addLabeled(JavaExecutionLocalize.jarApplicationConfigurationJarPathLabel(), myJarPathBox.getComponent());
            super.addBefore(builder);
        }

        @Override
        @RequiredUIAccess
        protected void addAfter(FormBuilder builder) {
            builder.addLabeled(JavaExecutionLocalize.runConfigurationJreLabel(), myJrePathEditor.getComponent());
            builder.addLabeled(JavaExecutionLocalize.jarApplicationConfigurationModuleLabel(), myModuleSelector.getComponent());
        }

        @Override
        @RequiredUIAccess
        public void apply(JarApplicationConfiguration configuration) {
            super.apply(configuration);

            configuration.setAlternativeJrePath(myJrePathEditor.getJrePathOrName());
            configuration.setAlternativeJrePathEnabled(myJrePathEditor.isAlternativeJreSelected());
            configuration.setJarPath(FileUtil.toSystemIndependentName(StringUtil.notNullize(myJarPathBox.getValue())));
            configuration.setModule(myModuleSelector.getModule());
        }

        @Override
        @RequiredUIAccess
        public void reset(JarApplicationConfiguration configuration) {
            super.reset(configuration);

            myJarPathBox.setValue(FileUtil.toSystemDependentName(configuration.getJarPath()));
            myJrePathEditor.setByName(configuration.isAlternativeJrePathEnabled() ? configuration.getAlternativeJrePath() : null);
            myModuleSelector.reset();
            myModuleSelector.setSelectedModule(configuration.getModule());
        }
    }
}
