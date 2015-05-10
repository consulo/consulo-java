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
package org.mustbe.consulo.java.module.extension;

import org.consulo.module.extension.impl.ModuleInheritableNamedPointerImpl;
import org.consulo.util.pointers.NamedPointer;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;

/**
 * @author VISTALL
 * @since 22:25/15.06.13
 */
public class LanguageLevelModuleInheritableNamedPointerImpl extends ModuleInheritableNamedPointerImpl<LanguageLevel>
{
	private final String myExtensionId;

	public LanguageLevelModuleInheritableNamedPointerImpl(@NotNull Project project, @NotNull String id)
	{
		super(project, "language-level");
		myExtensionId = id;
	}

	@Override
	public String getItemNameFromModule(@NotNull Module module)
	{
		final JavaModuleExtension extension = (JavaModuleExtension) ModuleUtilCore.getExtension(module, myExtensionId);
		if(extension != null)
		{
			return extension.getLanguageLevel().getName();
		}
		return null;
	}

	@Override
	public LanguageLevel getItemFromModule(@NotNull Module module)
	{
		final JavaModuleExtension extension = (JavaModuleExtension) ModuleUtilCore.getExtension(module, myExtensionId);
		if(extension != null)
		{
			return extension.getLanguageLevel();
		}
		return null;
	}

	@NotNull
	@Override
	public NamedPointer<LanguageLevel> getPointer(@NotNull Project project, @NotNull String name)
	{
		return LanguageLevel.valueOf(name);
	}

	@Override
	public LanguageLevel getDefaultValue()
	{
		return LanguageLevel.HIGHEST;
	}
}
