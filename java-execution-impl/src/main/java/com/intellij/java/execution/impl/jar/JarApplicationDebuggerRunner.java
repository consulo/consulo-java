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
package com.intellij.java.execution.impl.jar;

import com.intellij.java.debugger.impl.GenericDebuggerRunner;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.configuration.RunProfile;
import consulo.execution.debug.DefaultDebugExecutor;

import jakarta.annotation.Nonnull;

/**
 * @author nik
 */
@ExtensionImpl
public class JarApplicationDebuggerRunner extends GenericDebuggerRunner {
  @Override
  public boolean canRun(@Nonnull String executorId, @Nonnull RunProfile profile) {
    return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && profile instanceof JarApplicationConfiguration;
  }

  @Nonnull
  @Override
  public String getRunnerId() {
    return "JarDebug";
  }
}
