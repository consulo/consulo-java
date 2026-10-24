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
package com.intellij.java.execution;

import com.intellij.java.execution.configurations.ConfigurationWithAlternativeJre;
import consulo.execution.CommonProgramRunConfigurationParameters;
import consulo.java.execution.configurations.fragment.JreFragmentState;
import consulo.java.execution.configurations.fragment.VmParametersFragmentState;
import org.jspecify.annotations.Nullable;

/**
 * A Java run configuration: the shared program fragments plus VM options ({@link VmParametersFragmentState}) and the JRE
 * ({@link JreFragmentState}).
 */
public interface CommonJavaRunConfigurationParameters extends CommonProgramRunConfigurationParameters, ConfigurationWithAlternativeJre {
  default void setVMParameters(@Nullable String value) {
    getFragmentState(VmParametersFragmentState.class).setValue(value);
  }

  default @Nullable String getVMParameters() {
    return getFragmentState(VmParametersFragmentState.class).getValue();
  }

  @Override
  default boolean isAlternativeJrePathEnabled() {
    return getAlternativeJrePath() != null;
  }

  @Override
  default @Nullable String getAlternativeJrePath() {
    return getFragmentState(JreFragmentState.class).getPath();
  }

  /**
   * @param path a JDK name or a JRE home path, {@code null} for the JDK of the module or the project
   */
  default void setAlternativeJrePath(@Nullable String path) {
    getFragmentState(JreFragmentState.class).setPath(path);
  }

  @Nullable
  String getRunClass();

  @Nullable
  String getPackage();
}
