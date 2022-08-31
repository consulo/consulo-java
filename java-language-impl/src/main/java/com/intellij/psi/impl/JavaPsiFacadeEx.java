/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.TestOnly;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import consulo.disposer.Disposable;

public abstract class JavaPsiFacadeEx extends JavaPsiFacade
{
	@TestOnly
	public static JavaPsiFacadeEx getInstanceEx(@Nonnull Project project)
	{
		return (JavaPsiFacadeEx) getInstance(project);
	}

	@TestOnly
	@javax.annotation.Nullable
	public PsiClass findClass(@Nonnull String qualifiedName)
	{
		return findClass(qualifiedName, GlobalSearchScope.allScope(getProject()));
	}

	@TestOnly
	public abstract void setAssertOnFileLoadingFilter(@Nonnull VirtualFileFilter filter, @Nonnull Disposable parentDisposable);
}