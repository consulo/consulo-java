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
package com.intellij.java.execution.impl.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.JComboBox;

import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.module.ui.awt.ModuleDescriptionsComboBox;
import consulo.module.ui.awt.ModuleListCellRenderer;
import consulo.module.ui.awt.ModulesComboBox;
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.language.util.ModuleUtilCore;
import consulo.project.Project;
import consulo.module.ModulesAlphaComparator;
import com.intellij.java.language.psi.PsiClass;
import consulo.ui.ex.awt.ComboboxSpeedSearch;
import consulo.ui.ex.awt.SortedComboBoxModel;
import consulo.java.language.module.extension.JavaModuleExtension;
import jakarta.annotation.Nullable;

public class ConfigurationModuleSelector
{
	public static final String NO_MODULE_TEXT = "<no module>";
	private final Project myProject;
	/**
	 * this field is {@code null} if and only if {@link #myModulesList} is not null
	 */
	private final ModuleDescriptionsComboBox myModulesDescriptionsComboBox;
	/**
	 * this field is {@code null} if and only if {@link #myModulesDescriptionsComboBox} is not null
	 */
	private final JComboBox<Module> myModulesList;

	/**
	 * @deprecated use {@link #ConfigurationModuleSelector(Project, ModulesComboBox)} instead
	 */
	public ConfigurationModuleSelector(final Project project, final JComboBox<Module> modulesList)
	{
		this(project, modulesList, NO_MODULE_TEXT);
	}

	public ConfigurationModuleSelector(Project project, ModulesComboBox modulesComboBox)
	{
		this(project, modulesComboBox, NO_MODULE_TEXT);
	}

	public ConfigurationModuleSelector(Project project, ModuleDescriptionsComboBox modulesDescriptionsComboBox)
	{
		this(project, modulesDescriptionsComboBox, NO_MODULE_TEXT);
	}

	public ConfigurationModuleSelector(Project project, ModuleDescriptionsComboBox modulesDescriptionsComboBox, String emptySelectionText)
	{
		myProject = project;
		myModulesDescriptionsComboBox = modulesDescriptionsComboBox;
		myModulesList = null;
		modulesDescriptionsComboBox.allowEmptySelection(emptySelectionText);
	}

	public ConfigurationModuleSelector(Project project, ModulesComboBox modulesComboBox, String noModule)
	{
		myProject = project;
		myModulesList = modulesComboBox;
		myModulesDescriptionsComboBox = null;
		modulesComboBox.allowEmptySelection(noModule);
	}

	/**
	 * @deprecated use {@link #ConfigurationModuleSelector(Project, ModulesComboBox, String)} instead
	 */
	public ConfigurationModuleSelector(final Project project, final JComboBox<Module> modulesList, final String noModule)
	{
		myProject = project;
		myModulesList = modulesList;
		myModulesDescriptionsComboBox = null;
		new ComboboxSpeedSearch(modulesList)
		{
			protected String getElementText(Object element)
			{
				if(element instanceof Module)
				{
					return ((Module) element).getName();
				}
				else if(element == null)
				{
					return noModule;
				}
				return super.getElementText(element);
			}
		};
		myModulesList.setModel(new SortedComboBoxModel<>(ModulesAlphaComparator.INSTANCE));
		myModulesList.setRenderer(new ModuleListCellRenderer(noModule));
	}

	public void applyTo(final ModuleBasedConfiguration configurationModule)
	{
		if(myModulesList != null)
		{
			configurationModule.setModule((Module) myModulesList.getSelectedItem());
		}
		else
		{
			configurationModule.setModuleName(myModulesDescriptionsComboBox.getSelectedModuleName());
		}
	}

	public void reset(final ModuleBasedConfiguration configuration)
	{
		reset();
		if(myModulesList != null)
		{
			myModulesList.setSelectedItem(configuration.getConfigurationModule().getModule());
		}
		else
		{
			myModulesDescriptionsComboBox.setSelectedModule(myProject, configuration.getConfigurationModule().getModuleName());
		}
	}

	public void reset()
	{
		final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
		final List<Module> list = new ArrayList<>();
		for(final Module module : modules)
		{
			if(isModuleAccepted(module))
			{
				list.add(module);
			}
		}
		setModules(list);
	}

	public boolean isModuleAccepted(final Module module)
	{
		return ModuleUtilCore.getExtension(module, JavaModuleExtension.class) != null;
	}

	public Project getProject()
	{
		return myProject;
	}

	public JavaRunConfigurationModule getConfigurationModule()
	{
		final JavaRunConfigurationModule configurationModule = new JavaRunConfigurationModule(getProject(), false);
		configurationModule.setModule(getModule());
		return configurationModule;
	}

	private void setModules(final Collection<Module> modules)
	{
		if(myModulesDescriptionsComboBox != null)
		{
			myModulesDescriptionsComboBox.setModules(modules);
		}
		else if(myModulesList instanceof ModulesComboBox)
		{
			((ModulesComboBox) myModulesList).setModules(modules);
		}
		else
		{
			SortedComboBoxModel<Module> model = (SortedComboBoxModel<Module>) myModulesList.getModel();
			model.setAll(modules);
			model.add(null);
		}
	}

	public Module getModule()
	{
		return myModulesDescriptionsComboBox != null ? myModulesDescriptionsComboBox.getSelectedModule() : (Module) myModulesList.getSelectedItem();
	}

	@Nullable
	public PsiClass findClass(final String className)
	{
		return getConfigurationModule().findClass(className);
	}

	public String getModuleName()
	{
		final Module module = getModule();
		return module == null ? "" : module.getName();
	}
}
