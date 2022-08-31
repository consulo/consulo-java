/*
 * Copyright 2013 Consulo.org
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
package consulo.java.impl.module.extension;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Comparing;
import com.intellij.java.language.LanguageLevel;
import consulo.disposer.Disposable;
import consulo.java.language.module.extension.JavaMutableModuleExtension;
import consulo.java.language.module.extension.SpecialDirLocation;
import consulo.java.impl.module.extension.ui.JavaModuleExtensionPanel;
import consulo.module.extension.MutableModuleInheritableNamedPointer;
import consulo.module.extension.swing.SwingMutableModuleExtension;
import consulo.roots.ModuleRootLayer;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.util.List;

/**
 * @author VISTALL
 * @since 12:39/19.05.13
 */
public class JavaMutableModuleExtensionImpl extends JavaModuleExtensionImpl implements JavaMutableModuleExtension<JavaModuleExtensionImpl>, SwingMutableModuleExtension
{
	public JavaMutableModuleExtensionImpl(@Nonnull String id, @Nonnull ModuleRootLayer moduleRootLayer)
	{
		super(id, moduleRootLayer);
	}

	@RequiredUIAccess
	@Nullable
	@Override
	public JComponent createConfigurablePanel(@Nonnull Disposable disposable, @Nonnull Runnable updateOnCheck)
	{
		return new JavaModuleExtensionPanel(this, updateOnCheck);
	}

	@RequiredUIAccess
	@Nullable
	@Override
	public Component createConfigurationComponent(@Nonnull Disposable disposable, @Nonnull Runnable runnable)
	{
		return VerticalLayout.create().add(Label.create("Unsupported platform"));
	}

	@Override
	public void setEnabled(boolean val)
	{
		myIsEnabled = val;
	}

	@Override
	@Nonnull
	public MutableModuleInheritableNamedPointer<LanguageLevel> getInheritableLanguageLevel()
	{
		return myLanguageLevel;
	}

	@Override
	public void setBytecodeVersion(@Nullable String version)
	{
		myBytecodeVersion = version;
	}

	@Override
	public void setCompilerArguments(@Nonnull List<String> arguments)
	{
		myCompilerArguments.clear();
		myCompilerArguments.addAll(arguments);
	}

	@Override
	public void setSpecialDirLocation(@Nonnull SpecialDirLocation specialDirLocation)
	{
		mySpecialDirLocation = specialDirLocation;
	}

	@Override
	public boolean isModified(@Nonnull JavaModuleExtensionImpl javaModuleExtension)
	{
		if(isModifiedImpl(javaModuleExtension))
		{
			return true;
		}

		if(!myLanguageLevel.equals(javaModuleExtension.getInheritableLanguageLevel()))
		{
			return true;
		}

		if(!Comparing.equal(myBytecodeVersion, javaModuleExtension.getBytecodeVersion()))
		{
			return true;
		}

		if(!mySpecialDirLocation.equals(javaModuleExtension.getSpecialDirLocation()))
		{
			return true;
		}

		if(!myCompilerArguments.equals(javaModuleExtension.myCompilerArguments))
		{
			return true;
		}
		return false;
	}

	@Nonnull
	@Override
	public MutableModuleInheritableNamedPointer<Sdk> getInheritableSdk()
	{
		return (MutableModuleInheritableNamedPointer<Sdk>) super.getInheritableSdk();
	}
}
