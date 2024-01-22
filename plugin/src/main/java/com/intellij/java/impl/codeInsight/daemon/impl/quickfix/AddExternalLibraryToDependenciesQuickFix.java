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

import consulo.application.WriteAction;
import consulo.logging.Logger;
import consulo.codeEditor.Editor;
import consulo.module.Module;
import consulo.application.dumb.IndexNotReadyException;
import consulo.project.Project;
import consulo.module.content.layer.orderEntry.DependencyScope;
import com.intellij.java.language.projectRoots.roots.ExternalLibraryDescriptor;
import com.intellij.java.impl.openapi.roots.JavaProjectModelModificationService;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;

/**
 * @author nik
 */
class AddExternalLibraryToDependenciesQuickFix extends AddOrderEntryFix
{
	private static final Logger LOG = Logger.getInstance(AddExternalLibraryToDependenciesQuickFix.class);
	private final Module myCurrentModule;
	private final ExternalLibraryDescriptor myLibraryDescriptor;
	private final String myQualifiedClassName;

	public AddExternalLibraryToDependenciesQuickFix(@jakarta.annotation.Nonnull Module currentModule,
													@Nonnull ExternalLibraryDescriptor libraryDescriptor,
													@jakarta.annotation.Nonnull PsiReference reference,
													@Nullable String qualifiedClassName)
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
	@jakarta.annotation.Nonnull
	@Override
	public String getFamilyName()
	{
		return getText();
	}

	@Override
	public boolean isAvailable(@jakarta.annotation.Nonnull Project project, Editor editor, PsiFile file)
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