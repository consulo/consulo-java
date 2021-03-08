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
package com.intellij.psi.search;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;

/**
 * Allows to retrieve files and Java classes, methods and fields in a project by
 * non-qualified names.
 */
public abstract class PsiShortNamesCache
{
	/**
	 * Return the composite short names cache, uniting all short name cache instances registered via extensions.
	 *
	 * @param project the project to return the cache for.
	 * @return the cache instance.
	 */

	public static PsiShortNamesCache getInstance(Project project)
	{
		return ServiceManager.getService(project, PsiShortNamesCache.class);
	}

	public static final ExtensionPointName<PsiShortNamesCache> EP_NAME = ExtensionPointName.create("consulo.java.java.shortNamesCache");

	/**
	 * Returns the list of files with the specified name.
	 *
	 * @param name the name of the files to find.
	 * @return the list of files in the project which have the specified name.
	 */
	@Nonnull
	public PsiFile[] getFilesByName(@Nonnull String name)
	{
		return PsiFile.EMPTY_ARRAY;
	}

	/**
	 * Returns the list of names of all files in the project.
	 *
	 * @return the list of all file names in the project.
	 */
	@Nonnull
	public String[] getAllFileNames()
	{
		return ArrayUtil.EMPTY_STRING_ARRAY;
	}

	/**
	 * Returns the list of all classes with the specified name in the specified scope.
	 *
	 * @param name  the non-qualified name of the classes to find.
	 * @param scope the scope in which classes are searched.
	 * @return the list of found classes.
	 */
	@Nonnull
	public abstract PsiClass[] getClassesByName(@Nonnull @NonNls String name, @Nonnull GlobalSearchScope scope);

	/**
	 * Returns the list of names of all classes in the project and
	 * (optionally) libraries.
	 *
	 * @return the list of all class names.
	 */
	@Nonnull
	public abstract String[] getAllClassNames();

	public boolean processAllClassNames(Processor<String> processor)
	{
		return ContainerUtil.process(getAllClassNames(), processor);
	}

	public boolean processAllClassNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter)
	{
		return ContainerUtil.process(getAllClassNames(), processor);
	}

	/**
	 * Adds the names of all classes in the project and (optionally) libraries
	 * to the specified set.
	 *
	 * @param dest the set to add the names to.
	 */
	public abstract void getAllClassNames(@Nonnull HashSet<String> dest);

	/**
	 * Returns the list of all methods with the specified name in the specified scope.
	 *
	 * @param name  the name of the methods to find.
	 * @param scope the scope in which methods are searched.
	 * @return the list of found methods.
	 */
	@Nonnull
	public abstract PsiMethod[] getMethodsByName(@NonNls @Nonnull String name, @Nonnull GlobalSearchScope scope);

	@Nonnull
	public abstract PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @Nonnull String name, @Nonnull GlobalSearchScope scope, int maxCount);

	@Nonnull
	public abstract PsiField[] getFieldsByNameIfNotMoreThan(@NonNls @Nonnull String name, @Nonnull GlobalSearchScope scope, int maxCount);

	public abstract boolean processMethodsWithName(@NonNls @Nonnull String name, @Nonnull GlobalSearchScope scope,
			@Nonnull Processor<PsiMethod> processor);

	public abstract boolean processMethodsWithName(@NonNls @Nonnull String name, @Nonnull Processor<? super PsiMethod> processor,
			@Nonnull GlobalSearchScope scope, @Nullable IdFilter filter);

	public boolean processAllMethodNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter)
	{
		return ContainerUtil.process(getAllFieldNames(), processor);
	}

	public boolean processAllFieldNames(Processor<String> processor, GlobalSearchScope scope, IdFilter filter)
	{
		return ContainerUtil.process(getAllFieldNames(), processor);
	}

	/**
	 * Returns the list of names of all methods in the project and
	 * (optionally) libraries.
	 *
	 * @return the list of all method names.
	 */
	@Nonnull
	public abstract String[] getAllMethodNames();

	/**
	 * Adds the names of all methods in the project and (optionally) libraries
	 * to the specified set.
	 *
	 * @param set the set to add the names to.
	 */
	public abstract void getAllMethodNames(@Nonnull HashSet<String> set);

	/**
	 * Returns the list of all fields with the specified name in the specified scope.
	 *
	 * @param name  the name of the fields to find.
	 * @param scope the scope in which fields are searched.
	 * @return the list of found fields.
	 */
	@Nonnull
	public abstract PsiField[] getFieldsByName(@Nonnull @NonNls String name, @Nonnull GlobalSearchScope scope);

	/**
	 * Returns the list of names of all fields in the project and
	 * (optionally) libraries.
	 *
	 * @return the list of all field names.
	 */
	@Nonnull
	public abstract String[] getAllFieldNames();

	/**
	 * Adds the names of all methods in the project and (optionally) libraries
	 * to the specified set.
	 *
	 * @param set the set to add the names to.
	 */
	public abstract void getAllFieldNames(@Nonnull HashSet<String> set);

	public abstract boolean processFieldsWithName(@Nonnull String name, @Nonnull Processor<? super PsiField> processor,
			@Nonnull GlobalSearchScope scope, @Nullable IdFilter filter);

	public abstract boolean processClassesWithName(@Nonnull String name, @Nonnull Processor<? super PsiClass> processor,
			@Nonnull GlobalSearchScope scope, @Nullable IdFilter filter);
}
