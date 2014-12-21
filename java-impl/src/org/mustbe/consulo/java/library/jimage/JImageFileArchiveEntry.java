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

import org.consulo.lombok.annotations.LazyInstance;
import com.intellij.openapi.vfs.ArchiveEntry;
import com.intellij.util.ArrayUtil;
import consulo.internal.jdk.internal.jimage.ImageReader;

/**
 * @author VISTALL
 * @since 21.12.14
 */
public class JImageFileArchiveEntry implements ArchiveEntry
{
	private final ImageReader myImageReader;
	private final String myName;
	private final long myLastModified;

	public JImageFileArchiveEntry(ImageReader imageReader, String name, long lastModified)
	{
		myImageReader = imageReader;
		myName = name;
		myLastModified = lastModified;
	}

	@Override
	public String getName()
	{
		return myName;
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

	@LazyInstance
	public byte[] getBytes()
	{
		byte[] resource = ArrayUtil.EMPTY_BYTE_ARRAY;
		try
		{
			resource = myImageReader.getResource(myName);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		return resource;
	}
}
