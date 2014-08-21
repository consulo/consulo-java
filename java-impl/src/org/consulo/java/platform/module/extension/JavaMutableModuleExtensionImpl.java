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
package org.consulo.java.platform.module.extension;

import javax.swing.JComponent;

import org.consulo.java.module.extension.JavaMutableModuleExtension;
import org.consulo.java.platform.module.extension.ui.JavaModuleExtensionPanel;
import org.consulo.module.extension.MutableModuleInheritableNamedPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootLayer;
import com.intellij.pom.java.LanguageLevel;

/**
 * @author VISTALL
 * @since 12:39/19.05.13
 */
public class JavaMutableModuleExtensionImpl extends JavaModuleExtensionImpl implements JavaMutableModuleExtension<JavaModuleExtensionImpl>
{
	public JavaMutableModuleExtensionImpl(@NotNull String id, @NotNull ModuleRootLayer moduleRootLayer)
	{
		super(id, moduleRootLayer);
	}

	@Nullable
	@Override
	public JComponent createConfigurablePanel(@Nullable Runnable updateOnCheck)
	{
		return new JavaModuleExtensionPanel(this, updateOnCheck);
	}

	@Override
	public void setEnabled(boolean val)
	{
		myIsEnabled = val;
	}

	@Override
	@NotNull
	public MutableModuleInheritableNamedPointer<LanguageLevel> getInheritableLanguageLevel()
	{
		return myLanguageLevel;
	}

	@Override
	public void setSpecialDirLocation(@NotNull SpecialDirLocation specialDirLocation)
	{
		mySpecialDirLocation = specialDirLocation;
	}

	@Override
	public boolean isModified(@NotNull JavaModuleExtensionImpl javaModuleExtension)
	{
		if(isModifiedImpl(javaModuleExtension))
		{
			return true;
		}

		if(!myLanguageLevel.equals(javaModuleExtension.getInheritableLanguageLevel()))
		{
			return true;
		}

		if(!mySpecialDirLocation.equals(javaModuleExtension.getSpecialDirLocation()))
		{
			return true;
		}
		return false;
	}

	@NotNull
	@Override
	public MutableModuleInheritableNamedPointer<Sdk> getInheritableSdk()
	{
		return (MutableModuleInheritableNamedPointer<Sdk>) super.getInheritableSdk();
	}
}
