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

import com.intellij.util.ArrayUtil;
import consulo.internal.jdk.internal.jimage.ImageReader;
import consulo.java.library.jimage.JImageArchiveFile;
import consulo.lombok.annotations.Lazy;
import consulo.lombok.annotations.Logger;
import consulo.vfs.impl.archive.ArchiveEntry;

/**
 * @author VISTALL
 * @since 21.12.14
 */
@Logger
public class JImageFileArchiveEntry implements ArchiveEntry
{
	private final ImageReader myImageReader;
	private final ImageReader.Resource myResource;
	private final long myLastModified;

	public JImageFileArchiveEntry(ImageReader imageReader, ImageReader.Resource resource, long lastModified)
	{
		myImageReader = imageReader;
		myResource = resource;
		myLastModified = lastModified;
	}

	@Override
	public String getName()
	{
		return JImageArchiveFile.cutStartSlash(myResource.getNameString());
	}

	@Override
	public long getSize()
	{
		return getBytes().length;
	}

	@Override
	public long getTime()
	{
		return myLastModified;
	}

	@Override
	public boolean isDirectory()
	{
		return false;
	}

	@Lazy
	public byte[] getBytes()
	{
		try
		{
			return myImageReader.getResource(myResource);
		}
		catch(IOException e)
		{
			JImageFileArchiveEntry.LOGGER.error("Cant load data for: " + getName(), e);
			return ArrayUtil.EMPTY_BYTE_ARRAY;
		}
	}
}
