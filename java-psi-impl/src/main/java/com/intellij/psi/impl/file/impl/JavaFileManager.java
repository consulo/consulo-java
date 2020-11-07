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
 * @author max
 */
package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.search.GlobalSearchScope;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

public interface JavaFileManager
{
	static JavaFileManager getInstance(@Nonnull Project project)
	{
		return ServiceManager.getService(project, JavaFileManager.class);
	}

	@Nullable
	PsiClass findClass(@Nonnull String qName, @Nonnull GlobalSearchScope scope);

	PsiClass[] findClasses(@Nonnull String qName, @Nonnull GlobalSearchScope scope);

	Collection<String> getNonTrivialPackagePrefixes();

	@Nonnull
	Collection<PsiJavaModule> findModules(@Nonnull String moduleName, @Nonnull GlobalSearchScope scope);
}