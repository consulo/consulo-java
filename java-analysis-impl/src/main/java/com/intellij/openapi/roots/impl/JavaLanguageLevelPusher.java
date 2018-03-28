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
package com.intellij.openapi.roots.impl;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.messages.MessageBus;
import consulo.java.module.extension.JavaModuleExtension;

/**
 * @author Gregory.Shrago
 */
public class JavaLanguageLevelPusher implements FilePropertyPusher<LanguageLevel>
{

	public static void pushLanguageLevel(final Project project)
	{
		PushedFilePropertiesUpdater.getInstance(project).pushAll(new JavaLanguageLevelPusher());
	}

	@Override
	public void initExtra(@Nonnull Project project, @Nonnull MessageBus bus, @Nonnull Engine languageLevelUpdater)
	{
		// nothing
	}

	@Override
	@Nonnull
	public Key<LanguageLevel> getFileDataKey()
	{
		return LanguageLevel.KEY;
	}

	@Override
	public boolean pushDirectoriesOnly()
	{
		return true;
	}

	@Override
	@Nonnull
	public LanguageLevel getDefaultValue()
	{
		return LanguageLevel.HIGHEST;
	}

	@Override
	public LanguageLevel getImmediateValue(@Nonnull Project project, VirtualFile file)
	{
		if(file == null)
		{
			return null;
		}
		final Module moduleForFile = ModuleUtil.findModuleForFile(file, project);
		if(moduleForFile == null)
		{
			return null;
		}
		return getImmediateValue(moduleForFile);
	}

	@Override
	public LanguageLevel getImmediateValue(@Nonnull Module module)
	{
		ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

		final JavaModuleExtension extension = moduleRootManager.getExtension(JavaModuleExtension.class);
		return extension == null ? null : extension.getLanguageLevel();
	}

	@Override
	public boolean acceptsFile(@Nonnull VirtualFile file)
	{
		return false;
	}

	@Override
	public boolean acceptsDirectory(@Nonnull VirtualFile file, @Nonnull Project project)
	{
		return ProjectFileIndex.SERVICE.getInstance(project).isInSourceContent(file);
	}

	private static final FileAttribute PERSISTENCE = new FileAttribute("language_level_persistence", 2, true);

	@Override
	public void persistAttribute(@Nonnull Project project, @Nonnull VirtualFile fileOrDir, @Nonnull LanguageLevel level) throws IOException
	{
		final DataInputStream iStream = PERSISTENCE.readAttribute(fileOrDir);
		if(iStream != null)
		{
			try
			{
				final int oldLevelOrdinal = DataInputOutputUtil.readINT(iStream);
				if(oldLevelOrdinal == level.ordinal())
				{
					return;
				}
			}
			finally
			{
				iStream.close();
			}
		}

		final DataOutputStream oStream = PERSISTENCE.writeAttribute(fileOrDir);
		DataInputOutputUtil.writeINT(oStream, level.ordinal());
		oStream.close();

		for(VirtualFile child : fileOrDir.getChildren())
		{
			if(!child.isDirectory() && JavaFileType.INSTANCE == child.getFileType())
			{
				PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(child);
			}
		}
	}

	@Override
	public void afterRootsChanged(@Nonnull Project project)
	{
	}

	@Nullable
	public String getInconsistencyLanguageLevelMessage(@Nonnull String message, @Nonnull PsiElement element, @Nonnull LanguageLevel level, @Nonnull PsiFile file)
	{
		return null;
	}
}
