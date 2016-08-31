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
package com.intellij.openapi.roots.libraries;

import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import consulo.roots.types.BinariesOrderRootType;
import consulo.vfs.util.ArchiveVfsUtil;

/**
 * @author lesya
 */
public class LibrariesHelperImpl extends LibrariesHelper
{
	@Override
	public VirtualFile findJarByClass(Library library, @NonNls String fqn)
	{
		return library == null ? null : findRootByClass(Arrays.asList(library.getFiles(BinariesOrderRootType.getInstance())), fqn);
	}

	@Nullable
	@Override
	public VirtualFile findRootByClass(List<VirtualFile> roots, String fqn)
	{
		for(VirtualFile file : roots)
		{
			if(findInFile(file, fqn))
			{
				return file;
			}
		}
		return null;
	}

	@Override
	public boolean isClassAvailableInLibrary(Library library, String fqn)
	{
		final String[] urls = library.getUrls(BinariesOrderRootType.getInstance());
		return isClassAvailable(urls, fqn);
	}

	@Override
	public boolean isClassAvailable(final String[] urls, String fqn)
	{
		for(String url : urls)
		{
			VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
			if(file == null)
			{
				continue;
			}

			VirtualFile root = null;
			if(file.isDirectory())
			{
				root = file;
			}
			else
			{
				root = ArchiveVfsUtil.getArchiveRootForLocalFile(file);
			}

			if(root == null)
			{
				continue;
			}
			if(findInFile(root, fqn))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean findInFile(VirtualFile root, final String fqn)
	{
		String filePath = fqn.replace(".", "/") + "." + JavaClassFileType.INSTANCE.getDefaultExtension();

		return root.findFileByRelativePath(filePath) != null;
	}
}
