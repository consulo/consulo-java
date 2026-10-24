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
import consulo.annotation.component.ExtensionImpl;
import consulo.disposer.Disposable;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.execution.localize.ExecutionLocalize;
import consulo.java.execution.configurations.fragment.VmParametersFragmentState;
import consulo.java.execution.localize.JavaExecutionLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.cmd.ParametersListUtil;
import consulo.project.Project;
import consulo.ui.TextBoxWithExpandAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;

/**
 * VM options of a Java run configuration.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
@ExtensionImpl
public class VmParametersRunConfigurationFragment implements RunConfigurationFragment<RunConfiguration> {
    @Override
    public String getId() {
        return "vmParameters";
    }

    @Override
    public LocalizeValue getName() {
        return JavaExecutionLocalize.runConfigurationFragmentVmParameters();
    }

    @Override
    public LocalizeValue getGroup() {
        return JavaExecutionLocalize.runConfigurationFragmentGroupJava();
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.BEFORE_TYPE + 15;
    }

    @Override
    public boolean isApplicable(RunConfiguration configuration) {
        return configuration instanceof CommonJavaRunConfigurationParameters;
    }

    @Override
    public boolean isVisibleByDefault(RunConfiguration target) {
        return ((CommonJavaRunConfigurationParameters) target).getVMParameters() != null;
    }

    @Override
    public void applyHidden(RunConfiguration target) {
        ((CommonJavaRunConfigurationParameters) target).setVMParameters(null);
    }

    @Override
    public Class<VmParametersFragmentState> getStateClass() {
        return VmParametersFragmentState.class;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<RunConfiguration> createEditor(Project project, Disposable parent) {
        TextBoxWithExpandAction vmParameters = TextBoxWithExpandAction.create(
            PlatformIconGroup.actionsShow(),
            "",
            ParametersListUtil.DEFAULT_LINE_PARSER,
            ParametersListUtil.DEFAULT_LINE_JOINER
        );

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(ExecutionLocalize.runConfigurationJavaVmParametersLabel(), vmParameters);
            }

            @RequiredUIAccess
            @Override
            public void reset(RunConfiguration target) {
                vmParameters.setValue(StringUtil.notNullize(((CommonJavaRunConfigurationParameters) target).getVMParameters()));
            }

            @RequiredUIAccess
            @Override
            public void apply(RunConfiguration target) {
                ((CommonJavaRunConfigurationParameters) target).setVMParameters(vmParameters.getValue());
            }
        };
    }
}
