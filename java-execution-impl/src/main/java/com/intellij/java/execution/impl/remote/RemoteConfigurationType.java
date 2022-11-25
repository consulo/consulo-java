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

/*
 * Class RemoteConfigurationFactory
 * @author Jeka
 */
package com.intellij.java.execution.impl.remote;

import consulo.application.AllIcons;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationTypeBase;
import consulo.execution.configuration.RunConfiguration;
import consulo.java.execution.JavaExecutionBundle;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RemoteConfigurationType extends ConfigurationTypeBase {
  public RemoteConfigurationType() {
    super("JavaRemoteConfigurationType", JavaExecutionBundle.message("remote.debug.configuration.display.name"), JavaExecutionBundle.message("remote.debug.configuration.description"), AllIcons
        .RunConfigurations.Remote);
    addFactory(new ConfigurationFactory(this) {
      @Override
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new RemoteConfiguration(project, this);
      }

      @Override
      public boolean isApplicable(@Nonnull Project project) {
        return ModuleExtensionHelper.getInstance(project).hasModuleExtension(JavaModuleExtension.class);
      }
    });
  }

  @Nullable
  public static RemoteConfigurationType getInstance() {
    return EP_NAME.findExtensionOrFail(RemoteConfigurationType.class);
  }
}
