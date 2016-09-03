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

package consulo.java.library.jimage;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.VirtualFileManager;
import consulo.lombok.annotations.Logger;
import consulo.vfs.impl.archive.ArchiveFile;
import consulo.vfs.impl.archive.ArchiveFileSystemBase;

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

	public JImageFileSystem()
	{
		super(JImageFileType.PROTOCOL);
	}

	@NotNull
	@Override
	public ArchiveFile createArchiveFile(@NotNull String path) throws IOException
	{
		return new JImageArchiveFile(path);
	}
}
