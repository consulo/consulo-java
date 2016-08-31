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
package com.intellij.execution.remote;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mustbe.consulo.java.module.extension.JavaModuleExtension;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import consulo.module.extension.ModuleExtensionHelper;

public class RemoteConfigurationType implements ConfigurationType
{
	private final ConfigurationFactory myFactory;

	public RemoteConfigurationType()
	{
		myFactory = new ConfigurationFactory(this)
		{
			@Override
			public RunConfiguration createTemplateConfiguration(Project project)
			{
				return new RemoteConfiguration(project, this);
			}

			@Override
			public boolean isApplicable(@NotNull Project project)
			{
				return ModuleExtensionHelper.getInstance(project).hasModuleExtension(JavaModuleExtension.class);
			}
		};
	}

	@Override
	public String getDisplayName()
	{
		return ExecutionBundle.message("remote.debug.configuration.display.name");
	}

	@Override
	public String getConfigurationTypeDescription()
	{
		return ExecutionBundle.message("remote.debug.configuration.description");
	}

	@Override
	public Icon getIcon()
	{
		return AllIcons.RunConfigurations.Remote;
	}

	@Override
	public ConfigurationFactory[] getConfigurationFactories()
	{
		return new ConfigurationFactory[]{myFactory};
	}

	@NotNull
	public ConfigurationFactory getFactory()
	{
		return myFactory;
	}

	@Override
	@NotNull
	public String getId()
	{
		return "Remote";
	}

	@Nullable
	public static RemoteConfigurationType getInstance()
	{
		return ContainerUtil.findInstance(Extensions.getExtensions(CONFIGURATION_TYPE_EP), RemoteConfigurationType.class);
	}
}
