/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.java.execution.impl.application;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.configurations.JavaCommandLineState;
import com.intellij.java.execution.impl.JavaRunConfigurationExtensionManager;
import com.intellij.java.execution.impl.RunConfigurationExtension;
import com.intellij.java.execution.impl.util.JavaParametersUtil;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;

import javax.annotation.Nonnull;

/**
 * @author nik
 */
public abstract class BaseJavaApplicationCommandLineState<T extends RunConfigurationBase & CommonJavaRunConfigurationParameters> extends JavaCommandLineState {
  protected final T myConfiguration;

  public BaseJavaApplicationCommandLineState(ExecutionEnvironment environment, @Nonnull final T configuration) {
    super(environment);
    myConfiguration = configuration;
  }

  protected void setupJavaParameters(OwnJavaParameters params) throws ExecutionException {
    JavaParametersUtil.configureConfiguration(params, myConfiguration);

    for (RunConfigurationExtension ext : RunConfigurationExtension.EP_NAME.getExtensionList()) {
      ext.updateJavaParameters(getConfiguration(), params, getRunnerSettings());
    }
  }

  @Override
  protected void setupProcessHandler(@Nonnull ProcessHandler handler) {
    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(getConfiguration(), handler, getRunnerSettings());
  }

  @Override
  protected void buildProcessHandler(@Nonnull ProcessHandlerBuilder builder) throws ExecutionException {
    builder.colored().killable().silentReader();
  }

  @Override
  protected boolean ansiColoringEnabled() {
    return true;
  }

  protected T getConfiguration() {
    return myConfiguration;
  }
}
