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

import javax.annotation.Nonnull;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.java.language.LanguageLevel;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.module.extension.impl.ModuleInheritableNamedPointerImpl;
import consulo.roots.ModuleRootLayer;
import consulo.util.pointers.NamedPointer;

/**
 * @author VISTALL
 * @since 22:25/15.06.13
 */
public class LanguageLevelModuleInheritableNamedPointerImpl extends ModuleInheritableNamedPointerImpl<LanguageLevel>
{
	private final String myExtensionId;

	public LanguageLevelModuleInheritableNamedPointerImpl(@Nonnull ModuleRootLayer layer, @Nonnull String id)
	{
		super(layer, "language-level");
		myExtensionId = id;
	}

	@Override
	public String getItemNameFromModule(@Nonnull Module module)
	{
		final JavaModuleExtension extension = (JavaModuleExtension) ModuleUtilCore.getExtension(module, myExtensionId);
		if(extension != null)
		{
			return extension.getLanguageLevel().getName();
		}
		return null;
	}

	@Override
	public LanguageLevel getItemFromModule(@Nonnull Module module)
	{
		final JavaModuleExtension extension = (JavaModuleExtension) ModuleUtilCore.getExtension(module, myExtensionId);
		if(extension != null)
		{
			return extension.getLanguageLevel();
		}
		return null;
	}

	@Nonnull
	@Override
	public NamedPointer<LanguageLevel> getPointer(@Nonnull ModuleRootLayer layer, @Nonnull String name)
	{
		return LanguageLevel.valueOf(name);
	}
}
