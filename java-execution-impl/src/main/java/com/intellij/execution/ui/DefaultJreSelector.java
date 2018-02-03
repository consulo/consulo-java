/*
 * Copyright 2013-2018 consulo.io
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

package com.intellij.execution.ui;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import com.intellij.BundleBase;
import com.intellij.application.options.ModuleDescriptionsComboBox;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.util.ObjectUtil;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.module.extension.JavaModuleExtension;

/**
 * from kotlin
 */
public abstract class DefaultJreSelector
{
	private static class SdkFromModuleDependencies<T extends ComboBox<?>> extends DefaultJreSelector
	{
		private T moduleComboBox;
		private Function<T, Module> getSelectedModule;
		private Supplier<Boolean> productionOnly;

		public SdkFromModuleDependencies(T moduleComboBox, Function<T, Module> getSelectedModule, Supplier<Boolean> productionOnly)
		{
			this.moduleComboBox = moduleComboBox;
			this.getSelectedModule = getSelectedModule;
			this.productionOnly = productionOnly;
		}

		@NotNull
		@Override
		public Pair<String, String> getNameAndDescription()
		{
			Module module = getSelectedModule.apply(moduleComboBox);
			if(module == null)
			{
				return Pair.create(null, "module not specified");
			}

			Boolean productionOnly = this.productionOnly.get();
			Sdk jdkToRun = OwnJavaParameters.getJdkToRunModule(module, productionOnly);
			Sdk moduleJdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);

			if(jdkToRun == null || moduleJdk == null)
			{
				return Pair.create(null, "module not specified");
			}

			if(Objects.equals(moduleJdk.getHomeDirectory(), jdkToRun.getHomeDirectory()))
			{
				return Pair.create(moduleJdk.getName(), BundleBase.format("SDK of ''{0}'' module", module.getName()));
			}
			return Pair.create(jdkToRun.getName(), BundleBase.format("newest SDK from ''{0}'' module {1} dependencies", module.getName(), productionOnly ? "" : "test"));
		}

		@Override
		public void addChangeListener(Runnable runnable)
		{
			moduleComboBox.addActionListener(e -> runnable.run());
		}
	}

	private static class SdkFromSourceRootDependencies<T extends ComboBox<?>> extends SdkFromModuleDependencies<T>
	{
		private EditorTextFieldWithBrowseButton myClassSelector;

		public SdkFromSourceRootDependencies(T moduleComboBox, Function<T, Module> getSelectedModule, EditorTextFieldWithBrowseButton classSelector)
		{
			super(moduleComboBox, getSelectedModule, () -> isClassInProductionSources(moduleComboBox, getSelectedModule, classSelector));
			myClassSelector = classSelector;
		}

		@Override
		public void addChangeListener(Runnable runnable)
		{
			super.addChangeListener(runnable);
			myClassSelector.getChildComponent().addDocumentListener(new DocumentListener()
			{
				@Override
				public void documentChanged(DocumentEvent event)
				{
					runnable.run();
				}
			});
		}

		private static <T extends ComboBox<?>> boolean isClassInProductionSources(T moduleComboBox, Function<T, Module> getSelectedModule, EditorTextFieldWithBrowseButton classSelector)
		{
			Module module = getSelectedModule.apply(moduleComboBox);
			if(module == null)
			{
				return false;
			}
			return ObjectUtil.notNull(JavaParametersUtil.isClassInProductionSources(classSelector.getText(), module), Boolean.FALSE);
		}
	}

	@NotNull
	public static DefaultJreSelector fromModuleDependencies(ModulesComboBox modulesCombobox, boolean productionOnly)
	{
		return new SdkFromModuleDependencies<>(modulesCombobox, ModulesComboBox::getSelectedModule, () -> productionOnly);
	}

	@NotNull
	public static DefaultJreSelector fromModuleDependencies(ModuleDescriptionsComboBox modulesCombobox, boolean productionOnly)
	{
		return new SdkFromModuleDependencies<>(modulesCombobox, ModuleDescriptionsComboBox::getSelectedModule, () -> productionOnly);
	}

	@NotNull
	public static DefaultJreSelector fromSourceRootsDependencies(ModulesComboBox modulesCombobox, EditorTextFieldWithBrowseButton classSelector)
	{
		return new SdkFromSourceRootDependencies<>(modulesCombobox, ModulesComboBox::getSelectedModule, classSelector);
	}

	@NotNull
	public static DefaultJreSelector fromSourceRootsDependencies(ModuleDescriptionsComboBox modulesCombobox, EditorTextFieldWithBrowseButton classSelector)
	{
		return new SdkFromSourceRootDependencies<>(modulesCombobox, ModuleDescriptionsComboBox::getSelectedModule, classSelector);
	}

	@NotNull
	public abstract Pair<String, String> getNameAndDescription();

	public String getDescriptionString()
	{
		Pair<String, String> nameAndDescription = getNameAndDescription();
		return "( " + ObjectUtil.notNull(nameAndDescription.getFirst(), "<no JRE>") + " - " + nameAndDescription.getSecond() + ")";
	}

	public void addChangeListener(Runnable runnable)
	{
	}
}
