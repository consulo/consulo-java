/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.impl.file;

import org.jetbrains.annotations.NotNull;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import consulo.psi.PsiDirectoryMethodProxy;

public class JavaPsiDirectoryMethodProxy implements PsiDirectoryMethodProxy
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiJavaDirectoryImpl");


	@Override
	public boolean checkCreateFile(@NotNull PsiDirectory psiDirectory,  @NotNull final String name) throws IncorrectOperationException
	{
		final FileType type = FileTypeManager.getInstance().getFileTypeByFileName(name);
		if(type == JavaClassFileType.INSTANCE)
		{
			throw new IncorrectOperationException("Cannot create class-file");
		}

		return true;
	}

	@Override
	public PsiElement add(@NotNull PsiDirectory psiDirectory,  @NotNull final PsiElement element) throws IncorrectOperationException
	{
		if(element instanceof PsiClass)
		{
			final String name = ((PsiClass) element).getName();
			if(name != null)
			{
				final PsiClass newClass = JavaDirectoryService.getInstance().createClass(psiDirectory, name);
				return newClass.replace(element);
			}
			else
			{
				LOG.error("not implemented");
				return null;
			}
		}

		return null;
	}

	@Override
	public boolean checkAdd(@NotNull PsiDirectory psiDirectory,  @NotNull final PsiElement element) throws IncorrectOperationException
	{
		if(element instanceof PsiClass)
		{
			if(((PsiClass) element).getContainingClass() == null)
			{
				JavaDirectoryServiceImpl.checkCreateClassOrInterface(psiDirectory, ((PsiClass) element).getName());
			}
			else
			{
				LOG.error("not implemented");
			}
		}
		return true;
	}
}
