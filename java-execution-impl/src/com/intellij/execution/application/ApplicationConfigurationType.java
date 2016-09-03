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
package com.intellij.execution.application;

import javax.swing.Icon;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import consulo.java.module.extension.JavaModuleExtension;
import com.intellij.core.JavaCoreBundle;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.util.containers.ContainerUtil;
import consulo.module.extension.ModuleExtensionHelper;

public class ApplicationConfigurationType implements ConfigurationType
{
	private final ConfigurationFactory myFactory;

	public ApplicationConfigurationType()
	{
		myFactory = new ConfigurationFactoryEx(this)
		{
			@Override
			public RunConfiguration createTemplateConfiguration(Project project)
			{
				return new ApplicationConfiguration("", project, ApplicationConfigurationType.this);
			}

			@Override
			public boolean isApplicable(@NotNull Project project)
			{
				return ModuleExtensionHelper.getInstance(project).hasModuleExtension(JavaModuleExtension.class);
			}

			@Override
			public void onNewConfigurationCreated(@NotNull RunConfiguration configuration)
			{
				((ModuleBasedConfiguration) configuration).onNewConfigurationCreated();
			}
		};
	}

	@Override
	public String getDisplayName()
	{
		return JavaCoreBundle.message("application.configuration.name");
	}

	@Override
	public String getConfigurationTypeDescription()
	{
		return JavaCoreBundle.message("application.configuration.description");
	}

	@Override
	public Icon getIcon()
	{
		return AllIcons.RunConfigurations.Application;
	}

	@Override
	public ConfigurationFactory[] getConfigurationFactories()
	{
		return new ConfigurationFactory[]{myFactory};
	}

	@Nullable
	public static PsiClass getMainClass(PsiElement element)
	{
		while(element != null)
		{
			if(element instanceof PsiClass)
			{
				final PsiClass aClass = (PsiClass) element;
				if(PsiMethodUtil.findMainInClass(aClass) != null)
				{
					return aClass;
				}
			}
			else if(element instanceof PsiJavaFile)
			{
				final PsiJavaFile javaFile = (PsiJavaFile) element;
				final PsiClass[] classes = javaFile.getClasses();
				for(PsiClass aClass : classes)
				{
					if(PsiMethodUtil.findMainInClass(aClass) != null)
					{
						return aClass;
					}
				}
			}
			element = element.getParent();
		}
		return null;
	}


	@Override
	@NotNull
	@NonNls
	public String getId()
	{
		return "JavaApplication";
	}

	@Nullable
	public static ApplicationConfigurationType getInstance()
	{
		return ContainerUtil.findInstance(Extensions.getExtensions(CONFIGURATION_TYPE_EP), ApplicationConfigurationType.class);
	}

}
