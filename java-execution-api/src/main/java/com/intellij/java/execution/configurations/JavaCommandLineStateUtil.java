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
package com.intellij.java.execution.configurations;

import consulo.execution.process.ProcessTerminatedListener;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.local.ProcessHandlerFactory;

import javax.annotation.Nonnull;

/**
 * @author spleaner
 */
@Deprecated
public class JavaCommandLineStateUtil {
  private JavaCommandLineStateUtil() {
  }

  @Nonnull
  public static ProcessHandler startProcess(@Nonnull final GeneralCommandLine commandLine) throws ExecutionException {
    return startProcess(commandLine, false);
  }

  @Nonnull
  public static ProcessHandler startProcess(@Nonnull final GeneralCommandLine commandLine, final boolean ansiColoring) throws ExecutionException {
    ProcessHandler processHandler = ansiColoring ? ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine) : ProcessHandlerFactory.getInstance().createProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }
}
