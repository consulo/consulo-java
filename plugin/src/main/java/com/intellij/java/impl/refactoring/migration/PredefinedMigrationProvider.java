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
package com.intellij.java.impl.refactoring.migration;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

import javax.annotation.Nonnull;
import java.net.URL;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PredefinedMigrationProvider {
  ExtensionPointName<PredefinedMigrationProvider> EP_NAME = ExtensionPointName.create(PredefinedMigrationProvider.class);

  /**
   * URL should point to the file with serialized migration map.
   * <p>
   * The simplest way to prepare such map:
   * 1. Refactor|Migrate...
   * 2. Create new migration map with all settings needed
   * 3. Copy map's file from config/migration to the plugin's resources
   */
  @Nonnull
  URL getMigrationMap();
}
