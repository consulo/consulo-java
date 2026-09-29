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
package com.intellij.java.execution.impl.ui;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.ui.CommonProgramParametersLayout;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.cmd.ParametersListUtil;
import consulo.ui.TextBoxWithExpandAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.util.FormBuilder;
import org.jspecify.annotations.Nullable;

public class CommonJavaParametersLayout<P extends CommonJavaRunConfigurationParameters> extends CommonProgramParametersLayout<P> {
    private @Nullable TextBoxWithExpandAction myVMParametersComponent;

    public CommonJavaParametersLayout(DialogService dialogService) {
        super(dialogService);
    }

    @Override
    @RequiredUIAccess
    protected void addBefore(FormBuilder builder) {
        TextBoxWithExpandAction vmParametersComponent = TextBoxWithExpandAction.create(
            PlatformIconGroup.actionsShow(),
            "",
            ParametersListUtil.DEFAULT_LINE_PARSER,
            ParametersListUtil.DEFAULT_LINE_JOINER
        );
        builder.addLabeled(ExecutionLocalize.runConfigurationJavaVmParametersLabel(), vmParametersComponent);
        myVMParametersComponent = vmParametersComponent;
    }

    @RequiredUIAccess
    public void setVMParameters(@Nullable String text) {
        TextBoxWithExpandAction component = myVMParametersComponent;
        if (component != null) {
            component.setValue(text);
        }
    }

    public String getVMParameters() {
        TextBoxWithExpandAction component = myVMParametersComponent;
        return component == null ? "" : component.getValueOrError();
    }

    @Override
    @RequiredUIAccess
    public void apply(P configuration) {
        super.apply(configuration);
        configuration.setVMParameters(getVMParameters());
    }

    @Override
    @RequiredUIAccess
    public void reset(P configuration) {
        super.reset(configuration);
        setVMParameters(configuration.getVMParameters());
    }
}
