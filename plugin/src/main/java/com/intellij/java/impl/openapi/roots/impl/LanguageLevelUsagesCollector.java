/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.java.impl.openapi.roots.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.externalService.statistic.AbstractApplicationUsagesCollector;
import consulo.externalService.statistic.UsageDescriptor;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

@ExtensionImpl
public class LanguageLevelUsagesCollector extends AbstractApplicationUsagesCollector {
  @Nonnull
  @Override
  public String getGroupId() {
    return "consulo.java:language.level";
  }

  @Override
  @Nonnull
  public Set<UsageDescriptor> getProjectUsages(@Nonnull Project project) {
    final Set<String> languageLevels = new HashSet<String>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      JavaModuleExtension extension = ModuleRootManager.getInstance(module).getExtension(JavaModuleExtension.class);
      if (extension != null) {
        languageLevels.add(extension.getLanguageLevel().toString());
      }
    }
    return ContainerUtil.map2Set(languageLevels, languageLevel -> new UsageDescriptor(languageLevel, 1));
  }
}
