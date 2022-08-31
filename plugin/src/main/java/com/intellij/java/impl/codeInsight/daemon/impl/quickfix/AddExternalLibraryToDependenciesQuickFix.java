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

import com.intellij.openapi.application.WriteAction;
import consulo.logging.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.java.language.projectRoots.roots.ExternalLibraryDescriptor;
import com.intellij.java.impl.openapi.roots.JavaProjectModelModificationService;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;

/**
 * @author nik
 */
class AddExternalLibraryToDependenciesQuickFix extends AddOrderEntryFix
{
	private static final Logger LOG = Logger.getInstance(AddExternalLibraryToDependenciesQuickFix.class);
	private final Module myCurrentModule;
	private final ExternalLibraryDescriptor myLibraryDescriptor;
	private final String myQualifiedClassName;

	public AddExternalLibraryToDependenciesQuickFix(@Nonnull Module currentModule,
													@Nonnull ExternalLibraryDescriptor libraryDescriptor,
													@Nonnull PsiReference reference,
													@javax.annotation.Nullable String qualifiedClassName)
	{
		super(reference);
		myCurrentModule = currentModule;
		myLibraryDescriptor = libraryDescriptor;
		myQualifiedClassName = qualifiedClassName;
	}

	@Nls
	@Nonnull
	@Override
	public String getText()
	{
		return "Add '" + myLibraryDescriptor.getPresentableName() + "' to classpath";
	}

	@Nls
	@Nonnull
	@Override
	public String getFamilyName()
	{
		return getText();
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		return !project.isDisposed() && !myCurrentModule.isDisposed();
	}

	@Override
	public void invoke(@Nonnull Project project, final Editor editor, PsiFile file) throws IncorrectOperationException
	{
		DependencyScope scope = suggestScopeByLocation(myCurrentModule, myReference.getElement());
		JavaProjectModelModificationService.getInstance(project).addDependency(myCurrentModule, myLibraryDescriptor, scope).doWhenDone(aVoid -> WriteAction.run(() -> {
			try
			{
				importClass(myCurrentModule, editor, myReference, myQualifiedClassName);
			}
			catch(IndexNotReadyException e)
			{
				LOG.info(e);
			}
		}));
	}

	@Override
	public boolean startInWriteAction()
	{
		return false;
	}
}