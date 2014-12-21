/*
 * Copyright 2013-2014 must-be.org
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

package org.mustbe.consulo.java.library.jimage;

import java.io.IOException;

import org.consulo.lombok.annotations.Logger;
import org.consulo.vfs.ArchiveFileSystemBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.ArchiveFile;
import com.intellij.openapi.vfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.archive.ArchiveHandler;
import com.intellij.openapi.vfs.impl.archive.ArchiveHandlerBase;
import com.intellij.util.messages.MessageBus;

/**
 * @author VISTALL
 * @since 21.12.14
 */
@Logger
public class JImageFileSystem extends ArchiveFileSystemBase implements ApplicationComponent
{
	@NotNull
	public static JImageFileSystem getInstance()
	{
		return (JImageFileSystem) VirtualFileManager.getInstance().getFileSystem(JImageFileType.PROTOCOL);
	}

	public JImageFileSystem(MessageBus bus)
	{
		super(bus);
	}

	@Override
	public ArchiveHandler createHandler(ArchiveFileSystem fileSystem, String path)
	{
		return new ArchiveHandlerBase(fileSystem, path)
		{
			@Nullable
			@Override
			protected ArchiveFile createArchiveFile()
			{
				try
				{
					return new JImageArchiveFile(myBasePath);
				}
				catch(IOException e)
				{
					LOGGER.warn(e);
					return ArchiveFile.EMPTY;
				}
			}
		};
	}

	@Override
	public void initComponent()
	{

	}

	@Override
	public void disposeComponent()
	{

	}

	@NotNull
	@Override
	public String getProtocol()
	{
		return JImageFileType.PROTOCOL;
	}

	@NotNull
	@Override
	public String getComponentName()
	{
		return getClass().getSimpleName();
	}
}
