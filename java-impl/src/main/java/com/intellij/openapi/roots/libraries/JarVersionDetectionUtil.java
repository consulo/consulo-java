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
package com.intellij.openapi.roots.libraries;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.vfs.ArchiveFileSystem;

public class JarVersionDetectionUtil
{
	private JarVersionDetectionUtil()
	{
	}

	@Nullable
	public static String detectJarVersion(@Nonnull String detectionClass, @Nonnull Module module)
	{
		for(OrderEntry library : ModuleRootManager.getInstance(module).getOrderEntries())
		{
			if(library instanceof LibraryOrderEntry)
			{
				VirtualFile jar = LibrariesHelper.getInstance().findJarByClass(((LibraryOrderEntry) library).getLibrary(), detectionClass);
				if(jar != null && jar.getFileSystem() instanceof ArchiveFileSystem)
				{
					return getMainAttribute(jar, Attributes.Name.IMPLEMENTATION_VERSION);
				}
			}
		}

		return null;
	}

	@javax.annotation.Nullable
	public static String detectJarVersion(@Nonnull String detectionClass, @Nonnull List<VirtualFile> files)
	{
		VirtualFile jarRoot = LibrariesHelper.getInstance().findRootByClass(files, detectionClass);
		return jarRoot != null && jarRoot.getFileSystem() instanceof ArchiveFileSystem ? getMainAttribute(jarRoot, Attributes.Name.IMPLEMENTATION_VERSION) : null;
	}

	private static String getMainAttribute(VirtualFile jarRoot, Attributes.Name attribute)
	{
		VirtualFile manifestFile = jarRoot.findFileByRelativePath(JarFile.MANIFEST_NAME);
		if(manifestFile != null)
		{
			try (InputStream stream = manifestFile.getInputStream())
			{
				return new Manifest(stream).getMainAttributes().getValue(attribute);
			}
			catch(IOException e)
			{
				Logger.getInstance(JarVersionDetectionUtil.class).debug(e);
			}
		}

		return null;
	}

	@javax.annotation.Nullable
	public static String getBundleVersion(@Nonnull File jar)
	{
		return JarUtil.getJarAttribute(jar, new Attributes.Name("Bundle-Version"));
	}

	@javax.annotation.Nullable
	public static String getImplementationVersion(@Nonnull File jar)
	{
		return JarUtil.getJarAttribute(jar, Attributes.Name.IMPLEMENTATION_VERSION);
	}
}