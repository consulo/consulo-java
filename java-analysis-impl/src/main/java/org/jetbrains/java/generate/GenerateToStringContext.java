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

import javax.annotation.Nonnull;
import jakarta.inject.Singleton;

import org.jetbrains.java.generate.config.Config;
import consulo.component.persist.PersistentStateComponent;
import consulo.ide.ServiceManager;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;

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
public class GenerateToStringContext implements PersistentStateComponent<Config>
{
	@Nonnull
	public static GenerateToStringContext getInstance()
	{
		return ServiceManager.getService(GenerateToStringContext.class);
	}

	private Config config = new Config();

	public static Config getConfig()
	{
		return getInstance().config;
	}

	public static void setConfig(Config newConfig)
	{
		getInstance().config = newConfig;
	}

	@Override
	public Config getState()
	{
		return config;
	}

	@Override
	public void loadState(Config state)
	{
		config = state;
	}
}
