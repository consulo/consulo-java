/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate;

import com.intellij.java.analysis.impl.generate.config.Config;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.ide.ServiceManager;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;

/**
 * Application context for this plugin.
 */
@Singleton
@State(
  name = "ToStringSettings",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/other.xml"
    )
  }
)
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class GenerateToStringContext implements PersistentStateComponent<Config> {
  @Nonnull
  public static GenerateToStringContext getInstance() {
    return ServiceManager.getService(GenerateToStringContext.class);
  }

  private Config config = new Config();

  public static Config getConfig() {
    return getInstance().config;
  }

  public static void setConfig(Config newConfig) {
    getInstance().config = newConfig;
  }

  @Override
  public Config getState() {
    return config;
  }

  @Override
  public void loadState(Config state) {
    config = state;
  }
}
