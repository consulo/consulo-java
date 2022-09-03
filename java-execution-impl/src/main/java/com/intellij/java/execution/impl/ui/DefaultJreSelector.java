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

package com.intellij.java.execution.impl.ui;

import consulo.component.util.localize.BundleBase;
import consulo.module.ui.awt.ModuleDescriptionsComboBox;
import consulo.module.ui.awt.ModulesComboBox;
import com.intellij.java.execution.impl.util.JavaParametersUtil;
import consulo.document.event.DocumentEvent;
import consulo.document.event.DocumentListener;
import consulo.module.Module;
import consulo.language.util.ModuleUtilCore;
import consulo.content.bundle.Sdk;
import consulo.ui.ex.awt.ComboBox;
import consulo.util.lang.Pair;
import com.intellij.java.language.impl.ui.EditorTextFieldWithBrowseButton;
import consulo.util.lang.ObjectUtil;
import consulo.annotation.DeprecationInfo;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.language.module.extension.JavaModuleExtension;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * from kotlin
 */
@Deprecated
@DeprecationInfo("From IDEA merge - unused")
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

		@Nonnull
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

	@Nonnull
	public static DefaultJreSelector fromModuleDependencies(ModulesComboBox modulesCombobox, boolean productionOnly)
	{
		return new SdkFromModuleDependencies<>(modulesCombobox, ModulesComboBox::getSelectedModule, () -> productionOnly);
	}

	@Nonnull
	public static DefaultJreSelector fromModuleDependencies(ModuleDescriptionsComboBox modulesCombobox, boolean productionOnly)
	{
		return new SdkFromModuleDependencies<>(modulesCombobox, ModuleDescriptionsComboBox::getSelectedModule, () -> productionOnly);
	}

	@Nonnull
	public static DefaultJreSelector fromSourceRootsDependencies(ModulesComboBox modulesCombobox, EditorTextFieldWithBrowseButton classSelector)
	{
		return new SdkFromSourceRootDependencies<>(modulesCombobox, ModulesComboBox::getSelectedModule, classSelector);
	}

	@Nonnull
	public static DefaultJreSelector fromSourceRootsDependencies(ModuleDescriptionsComboBox modulesCombobox, EditorTextFieldWithBrowseButton classSelector)
	{
		return new SdkFromSourceRootDependencies<>(modulesCombobox, ModuleDescriptionsComboBox::getSelectedModule, classSelector);
	}

	@Nonnull
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
