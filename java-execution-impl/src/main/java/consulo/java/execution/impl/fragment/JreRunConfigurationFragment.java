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
package consulo.java.execution.impl.fragment;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.impl.ui.UnifiedJrePathEditor;
import consulo.annotation.component.ExtensionImpl;
import consulo.disposer.Disposable;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.java.execution.configurations.fragment.JreFragmentState;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * The JRE of a Java run configuration; "Auto Select" keeps the JDK of the module or the project.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@ExtensionImpl
public class JreRunConfigurationFragment implements RunConfigurationFragment<RunConfiguration> {
    @Override
    public String getId() {
        return "jrePath";
    }

    @Override
    public LocalizeValue getName() {
        return JavaExecutionLocalize.runConfigurationFragmentJre();
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.BEFORE_TYPE + 5;
    }

    @Override
    public boolean isRemovable() {
        return false;
    }

    @Override
    public boolean isApplicable(RunConfiguration configuration) {
        return configuration instanceof CommonJavaRunConfigurationParameters;
    }

    @Override
    public Class<JreFragmentState> getStateClass() {
        return JreFragmentState.class;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<RunConfiguration> createEditor(Project project, Disposable parent) {
        UnifiedJrePathEditor jrePathEditor = new UnifiedJrePathEditor(parent);

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(JavaExecutionLocalize.runConfigurationJreLabel(), jrePathEditor.getComponent());
            }

            @RequiredUIAccess
            @Override
            public void reset(RunConfiguration target) {
                jrePathEditor.setByName(((CommonJavaRunConfigurationParameters) target).getAlternativeJrePath());
            }

            @RequiredUIAccess
            @Override
            public void apply(RunConfiguration target) {
                ((CommonJavaRunConfigurationParameters) target).setAlternativeJrePath(
                    jrePathEditor.isAlternativeJreSelected() ? jrePathEditor.getJrePathOrName() : null
                );
            }
        };
    }
}
