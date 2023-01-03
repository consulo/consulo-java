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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.execution.configurations.RemoteConnection;
import com.intellij.java.execution.configurations.RemoteState;
import consulo.execution.DefaultExecutionResult;
import consulo.execution.ExecutionResult;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ProgramRunner;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.process.ExecutionException;
import consulo.project.Project;

import javax.annotation.Nonnull;

/**
 * @author lex
 */
public class RemoteStateState implements RemoteState {
  private final Project    myProject;
  private final RemoteConnection myConnection;

  public RemoteStateState(Project project,
                          RemoteConnection connection) {
    myProject = project;
    myConnection = connection;
  }

  public ExecutionResult execute(final Executor executor, @Nonnull final ProgramRunner runner) throws ExecutionException {
    ConsoleView consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(myProject).getConsole();
    RemoteDebugProcessHandler process = new RemoteDebugProcessHandler(myProject);
    consoleView.attachToProcess(process);
    return new DefaultExecutionResult(consoleView, process);
  }

  public RemoteConnection getRemoteConnection() {
    return myConnection;
  }

}
