/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.spi;

import com.intellij.icons.AllIcons;
import com.intellij.java.language.spi.SPILanguage;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * User: anna
 */
public class SPIFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile
{
	public static final SPIFileType INSTANCE = new SPIFileType();

	private SPIFileType()
	{
		super(SPILanguage.INSTANCE);
	}

	@Override
	public boolean isMyFileType(@Nonnull VirtualFile file)
	{
		VirtualFile parent = file.getParent();
		if(parent != null && Objects.equals("services", parent.getNameSequence()))
		{
			final VirtualFile gParent = parent.getParent();
			if(gParent != null && Objects.equals("META-INF", gParent.getNameSequence()))
			{
				final String fileName = file.getName();
				return FileTypeRegistry.getInstance().getFileTypeByFileName(fileName) == UnknownFileType.INSTANCE;
			}
		}
		return false;
	}

	@Nonnull
	@Override
	public String getId()
	{
		return "JAVA-SPI";
	}

	@Nonnull
	@Override
	public LocalizeValue getDescription()
	{
		return LocalizeValue.localizeTODO("Service Provider Interface");
	}

	@Nonnull
	@Override
	public Image getIcon()
	{
		return AllIcons.FileTypes.Text;
	}

	@Nullable
	@Override
	public String getCharset(@Nonnull VirtualFile file, byte[] content)
	{
		return CharsetToolkit.UTF8;
	}
}
