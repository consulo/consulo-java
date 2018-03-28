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
package consulo.java.module.extension;

import javax.annotation.Nonnull;
import javax.swing.JComponent;

import javax.annotation.Nullable;

import consulo.java.module.extension.ui.JavaModuleExtensionPanel;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import consulo.annotations.RequiredDispatchThread;
import consulo.module.extension.MutableModuleInheritableNamedPointer;
import consulo.roots.ModuleRootLayer;

/**
 * @author VISTALL
 * @since 12:39/19.05.13
 */
public class JavaMutableModuleExtensionImpl extends JavaModuleExtensionImpl implements JavaMutableModuleExtension<JavaModuleExtensionImpl>
{
	public JavaMutableModuleExtensionImpl(@Nonnull String id, @Nonnull ModuleRootLayer moduleRootLayer)
	{
		super(id, moduleRootLayer);
	}

	@Nullable
	@Override
	@RequiredDispatchThread
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
		return false;
	}

	@Nonnull
	@Override
	public MutableModuleInheritableNamedPointer<Sdk> getInheritableSdk()
	{
		return (MutableModuleInheritableNamedPointer<Sdk>) super.getInheritableSdk();
	}
}
