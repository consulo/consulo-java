/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.execution.impl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunConfigurationExtensionBase;
import consulo.execution.configuration.RunnerSettings;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.psi.PsiElement;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/*
 * @author yole
 * @since 2007-01-31
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class RunConfigurationExtension extends RunConfigurationExtensionBase<RunConfigurationBase> {
    public static final ExtensionPointName<RunConfigurationExtension> EP_NAME = ExtensionPointName.create(RunConfigurationExtension.class);

    public abstract <T extends RunConfigurationBase> void updateJavaParameters(
        T configuration,
        OwnJavaParameters params,
        RunnerSettings runnerSettings
    ) throws ExecutionException;

    @Override
    protected void patchCommandLine(
        @Nonnull RunConfigurationBase configuration,
        RunnerSettings runnerSettings,
        @Nonnull GeneralCommandLine cmdLine,
        @Nonnull String runnerId
    ) throws ExecutionException {
    }

    @Override
    protected boolean isEnabledFor(@Nonnull RunConfigurationBase applicableConfiguration, @Nullable RunnerSettings runnerSettings) {
        return true;
    }

    @Override
    protected void extendTemplateConfiguration(@Nonnull RunConfigurationBase configuration) {
    }

    public void cleanUserData(RunConfigurationBase runConfigurationBase) {
    }

    public static void cleanExtensionsUserData(RunConfigurationBase runConfigurationBase) {
        for (RunConfigurationExtension extension : EP_NAME.getExtensionList()) {
            extension.cleanUserData(runConfigurationBase);
        }
    }

    public RefactoringElementListener wrapElementListener(
        PsiElement element,
        RunConfigurationBase runJavaConfiguration,
        RefactoringElementListener listener
    ) {
        return listener;
    }

    public static RefactoringElementListener wrapRefactoringElementListener(
        PsiElement element,
        RunConfigurationBase runConfigurationBase,
        RefactoringElementListener listener
    ) {
        for (RunConfigurationExtension extension : EP_NAME.getExtensions()) {
            listener = extension.wrapElementListener(element, runConfigurationBase, listener);
        }
        return listener;
    }

    public boolean isListenerDisabled(RunConfigurationBase configuration, Object listener, RunnerSettings runnerSettings) {
        return false;
    }
}