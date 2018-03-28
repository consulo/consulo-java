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
package com.intellij.codeInsight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;

public class ReadableExternalAnnotationsManager extends BaseExternalAnnotationsManager
{
	@javax.annotation.Nullable
	private Set<VirtualFile> myAnnotationsRoots = null;

	public ReadableExternalAnnotationsManager(PsiManager psiManager)
	{
		super(psiManager);
	}

	@Override
	protected boolean hasAnyAnnotationsRoots()
	{
		return !initRoots().isEmpty();
	}

	@Nonnull
	private synchronized Set<VirtualFile> initRoots()
	{
		if(myAnnotationsRoots == null)
		{
			myAnnotationsRoots = new HashSet<VirtualFile>();
			final Module[] modules = ModuleManager.getInstance(myPsiManager.getProject()).getModules();
			for(Module module : modules)
			{
				for(OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries())
				{
					final VirtualFile[] files = AnnotationOrderRootType.getFiles(entry);
					if(files.length > 0)
					{
						Collections.addAll(myAnnotationsRoots, files);
					}
				}
			}
		}
		return myAnnotationsRoots;
	}

	@Override
	@Nonnull
	protected List<VirtualFile> getExternalAnnotationsRoots(@Nonnull VirtualFile libraryFile)
	{
		ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myPsiManager.getProject()).getFileIndex();
		Set<VirtualFile> result = new LinkedHashSet<VirtualFile>();
		for(OrderEntry entry : fileIndex.getOrderEntriesForFile(libraryFile))
		{
			if(!(entry instanceof ModuleOrderEntry))
			{
				Collections.addAll(result, AnnotationOrderRootType.getFiles(entry));
			}
		}
		return new ArrayList<VirtualFile>(result);
	}

	@Override
	protected synchronized void dropCache()
	{
		myAnnotationsRoots = null;
		super.dropCache();
	}

	public boolean isUnderAnnotationRoot(VirtualFile file)
	{
		return VfsUtilCore.isUnder(file, initRoots());
	}
}
