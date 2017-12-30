/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl;

import java.util.Collection;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.JavaProjectModelModifier;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.pom.java.LanguageLevel;

/**
 * @author nik
 */
public class JavaProjectModelModificationServiceImpl extends JavaProjectModelModificationService
{
	private final Project myProject;

	public JavaProjectModelModificationServiceImpl(Project project)
	{
		myProject = project;
	}

	@Override
	public AsyncResult<Void> addDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope)
	{
		for(JavaProjectModelModifier modifier : getModelModifiers())
		{
			AsyncResult<Void> asyncResult = modifier.addModuleDependency(from, to, scope);
			if(asyncResult != null)
			{
				return asyncResult;
			}
		}
		return AsyncResult.rejected();
	}

	@Override
	public AsyncResult<Void> addDependency(@NotNull Collection<Module> from, @NotNull ExternalLibraryDescriptor libraryDescriptor, @NotNull DependencyScope scope)
	{
		for(JavaProjectModelModifier modifier : getModelModifiers())
		{
			AsyncResult<Void> asyncResult = modifier.addExternalLibraryDependency(from, libraryDescriptor, scope);
			if(asyncResult != null)
			{
				return asyncResult;
			}
		}
		return AsyncResult.rejected();
	}

	@Override
	public AsyncResult<Void> addDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope)
	{
		for(JavaProjectModelModifier modifier : getModelModifiers())
		{
			AsyncResult<Void> asyncResult = modifier.addLibraryDependency(from, library, scope);
			if(asyncResult != null)
			{
				return asyncResult;
			}
		}
		return AsyncResult.rejected();
	}

	@Override
	public AsyncResult<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel languageLevel)
	{
		for(JavaProjectModelModifier modifier : getModelModifiers())
		{
			AsyncResult<Void> asyncResult = modifier.changeLanguageLevel(module, languageLevel);
			if(asyncResult != null)
			{
				return asyncResult;
			}
		}
		return AsyncResult.rejected();
	}

	@NotNull
	private JavaProjectModelModifier[] getModelModifiers()
	{
		return JavaProjectModelModifier.EP_NAME.getExtensions(myProject);
	}
}