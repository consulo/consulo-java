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
package com.intellij.java.debugger.impl;

import consulo.execution.executor.Executor;
import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.execution.configuration.RunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import consulo.ui.ex.action.*;
import consulo.ui.ex.action.Constraints;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DefaultDebugUIEnvironment implements DebugUIEnvironment {
  private final ExecutionEnvironment myExecutionEnvironment;
  private final DebugEnvironment myModelEnvironment;

  public DefaultDebugUIEnvironment(@Nonnull ExecutionEnvironment environment, RunProfileState state, RemoteConnection remoteConnection,
                                   boolean pollConnection) {
    myExecutionEnvironment = environment;
    myModelEnvironment = new DefaultDebugEnvironment(environment, state, remoteConnection, pollConnection);
  }

  @Override
  public DebugEnvironment getEnvironment() {
    return myModelEnvironment;
  }

  @Nullable
  @Override
  public RunContentDescriptor getReuseContent() {
    return myExecutionEnvironment.getContentToReuse();
  }

  @Override
  public Image getIcon() {
    return getRunProfile().getIcon();
  }

  @Override
  public void initActions(RunContentDescriptor content, DefaultActionGroup actionGroup) {
    Executor executor = myExecutionEnvironment.getExecutor();
    actionGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_RERUN), Constraints.FIRST);

    actionGroup.add(new CloseAction(executor, content, myExecutionEnvironment.getProject()));
    actionGroup.add(new ContextHelpAction(executor.getHelpId()));
  }

  @Override
  @Nonnull
  public RunProfile getRunProfile() {
    return myExecutionEnvironment.getRunProfile();
  }
}
