/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.language.jvm.facade;

import static consulo.util.collection.ContainerUtil.getFirstItem;

import java.util.List;

import javax.annotation.Nonnull;

import consulo.ide.ServiceManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import com.intellij.java.language.jvm.JvmClass;
import consulo.application.progress.ProgressManager;

public interface JvmFacade
{

	@Nonnull
	static JvmFacade getInstance(@Nonnull Project project)
	{
		return ServiceManager.getService(project, JvmFacade.class);
	}

	/**
	 * Searches the specified scope within the project for a class with the specified full-qualified
	 * name and returns one if it is found.
	 *
	 * @param qualifiedName the full-qualified name of the class to find.
	 * @param scope         the scope to search.
	 * @return the PSI class, or null if no class with such name is found.
	 */
	@Nullable
	default JvmClass findClass(@NonNls @Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope)
	{
		ProgressManager.checkCanceled();
		return getFirstItem(findClasses(qualifiedName, scope));
	}

	/**
	 * Searches the specified scope within the project for classes with the specified full-qualified
	 * name and returns all found classes.
	 *
	 * @param qualifiedName the full-qualified name of the class to find.
	 * @param scope         the scope to search.
	 * @return the array of found classes, or an empty array if no classes are found.
	 */
	@Nonnull
	List<? extends JvmClass> findClasses(@NonNls @Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope);
}
