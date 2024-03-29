/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.externalSystem;

import com.intellij.java.debugger.impl.GenericDebuggerRunner;
import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.configuration.RunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.RunContentDescriptor;
import consulo.externalSystem.service.execution.ExternalSystemRunConfiguration;
import consulo.externalSystem.util.ExternalSystemConstants;
import consulo.logging.Logger;
import consulo.process.ExecutionException;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Denis Zhdanov
 * @since 6/7/13 11:18 AM
 */
@ExtensionImpl
public class ExternalSystemTaskDebugRunner extends GenericDebuggerRunner {
  private static final Logger LOG = Logger.getInstance(ExternalSystemTaskDebugRunner.class);

  @Nonnull
  @Override
  public String getRunnerId() {
    return ExternalSystemConstants.DEBUG_RUNNER_ID;
  }

  @Override
  public boolean canRun(@Nonnull String executorId, @Nonnull RunProfile profile) {
    return profile instanceof ExternalSystemRunConfiguration && DefaultDebugExecutor.EXECUTOR_ID.equals(executorId);
  }

  @Nullable
  @Override
  protected RunContentDescriptor createContentDescriptor(@Nonnull RunProfileState state, @Nonnull ExecutionEnvironment environment) throws
      ExecutionException {
    if (state instanceof ExternalSystemRunConfiguration.MyRunnableState) {
      int port = ((ExternalSystemRunConfiguration.MyRunnableState) state).getDebugPort();
      if (port > 0) {
        RemoteConnection connection = new RemoteConnection(true, "127.0.0.1", String.valueOf(port), true);
        return attachVirtualMachine(state, environment, connection, true);
      } else {
        LOG.warn("Can't attach debugger to external system task execution. Reason: target debug port is unknown");
      }
    } else {
      LOG.warn(String.format("Can't attach debugger to external system task execution. Reason: invalid run profile state is provided" + "- " +
          "expected '%s' but got '%s'", ExternalSystemRunConfiguration.MyRunnableState.class.getName(), state.getClass().getName()));
    }
    return null;
  }
}
