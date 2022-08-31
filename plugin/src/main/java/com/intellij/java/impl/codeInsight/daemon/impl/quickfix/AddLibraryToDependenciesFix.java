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
package com.intellij.java.impl.codeInsight.daemon.impl.quickfix;

import javax.annotation.Nonnull;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.java.impl.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import consulo.java.analysis.impl.JavaQuickFixBundle;

/**
 * @author nik
 */
class AddLibraryToDependenciesFix extends AddOrderEntryFix
{
	private final Library myLibrary;
	private final Module myCurrentModule;
	private final String myQualifiedClassName;

	public AddLibraryToDependenciesFix(@Nonnull Module currentModule, @Nonnull Library library, @Nonnull PsiReference reference, @javax.annotation.Nullable String qualifiedClassName)
	{
		super(reference);
		myLibrary = library;
		myCurrentModule = currentModule;
		myQualifiedClassName = qualifiedClassName;
	}

	@Override
	@Nonnull
	public String getText()
	{
		return JavaQuickFixBundle.message("orderEntry.fix.add.library.to.classpath", LibraryUtil.getPresentableName(myLibrary));
	}

	@Override
	@Nonnull
	public String getFamilyName()
	{
		return JavaQuickFixBundle.message("orderEntry.fix.family.add.library.to.classpath");
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		return !project.isDisposed() && !myCurrentModule.isDisposed() && !((LibraryEx) myLibrary).isDisposed();
	}

	@Override
	public void invoke(@Nonnull Project project, @javax.annotation.Nullable Editor editor, PsiFile file)
	{
		DependencyScope scope = suggestScopeByLocation(myCurrentModule, myReference.getElement());
		JavaProjectModelModificationService.getInstance(project).addDependency(myCurrentModule, myLibrary, scope);
		if(myQualifiedClassName != null && editor != null)
		{
			importClass(myCurrentModule, editor, myReference, myQualifiedClassName);
		}
	}
}