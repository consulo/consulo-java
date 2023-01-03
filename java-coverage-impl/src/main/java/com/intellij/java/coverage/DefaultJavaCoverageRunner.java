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
package com.intellij.java.coverage;

import com.intellij.java.execution.impl.DefaultJavaProgramRunner;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.configuration.*;
import consulo.execution.coverage.CoverageEngine;
import consulo.execution.coverage.CoverageExecutor;
import consulo.execution.coverage.CoverageRunnerData;

import javax.annotation.Nonnull;

@ExtensionImpl(id = "DefaultJavaCoverageRunner")
public class DefaultJavaCoverageRunner extends DefaultJavaProgramRunner {
  public boolean canRun(@Nonnull final String executorId, @Nonnull final RunProfile profile) {
    try {
      return executorId.equals(CoverageExecutor.EXECUTOR_ID) &&
          //profile instanceof ModuleBasedConfiguration &&
          !(profile instanceof RunConfigurationWithSuppressedDefaultRunAction) &&
          profile instanceof RunConfigurationBase &&
          CoverageEngine.EP_NAME.findExtension(JavaCoverageEngine.class).isApplicableTo((RunConfigurationBase) profile);
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public RunnerSettings createConfigurationData(ConfigurationInfoProvider settingsProvider) {
    return new CoverageRunnerData();
  }

  @Nonnull
  @Override
  public String getRunnerId() {
    return "Cover";
  }
}
