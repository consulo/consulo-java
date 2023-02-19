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
package com.intellij.java.execution.configurations;

import consulo.execution.configuration.CommandLineState;
import consulo.execution.process.ProcessTerminatedListener;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.cmd.GeneralCommandLine;

import javax.annotation.Nonnull;

public abstract class JavaCommandLineState extends CommandLineState implements JavaCommandLine {
  private OwnJavaParameters myParams;

  protected JavaCommandLineState(@Nonnull ExecutionEnvironment environment) {
    super(environment);
  }

  @Override
  public OwnJavaParameters getJavaParameters() throws ExecutionException {
    if (myParams == null) {
      myParams = createJavaParameters();
    }
    return myParams;
  }

  public void clear() {
    myParams = null;
  }

  protected void buildProcessHandler(@Nonnull ProcessHandlerBuilder builder) throws ExecutionException {
    if (ansiColoringEnabled()) {
      builder.colored();
    }
  }

  protected void setupProcessHandler(@Nonnull ProcessHandler handler) {
    ProcessTerminatedListener.attach(handler);
  }

  @Override
  @Nonnull
  protected final ProcessHandler startProcess() throws ExecutionException {
    ProcessHandlerBuilder builder = ProcessHandlerBuilder.create(createCommandLine());
    buildProcessHandler(builder);

    ProcessHandler handler = builder.build();
    setupProcessHandler(handler);
    return handler;
  }

  protected boolean ansiColoringEnabled() {
    return true;
  }

  protected abstract OwnJavaParameters createJavaParameters() throws ExecutionException;

  protected GeneralCommandLine createCommandLine() throws ExecutionException {
    OwnJavaParameters javaParameters = getJavaParameters();
    if (!javaParameters.isDynamicClasspath()) {
      javaParameters.setUseDynamicClasspath(getEnvironment().getProject());
    }
    return javaParameters.toCommandLine();
  }

  public boolean shouldAddJavaProgramRunnerActions() {
    return true;
  }
}