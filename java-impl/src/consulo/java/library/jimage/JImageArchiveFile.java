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

import gnu.trove.THashMap;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.ArrayUtil;
import consulo.internal.jdk.internal.jimage.Consumer;
import consulo.internal.jdk.internal.jimage.ImageReader;
import consulo.internal.jdk.internal.jimage.UTF8String;
import consulo.vfs.impl.archive.ArchiveEntry;
import consulo.vfs.impl.archive.ArchiveFile;

/**
 * @author VISTALL
 * @since 21.12.14
 */
public class JImageArchiveFile implements ArchiveFile
{
	private final Map<String, ArchiveEntry> myEntries;

	private String myName;

	public JImageArchiveFile(String basePath) throws IOException
	{
		File file = new File(basePath);
		myName = file.getName();

		final ImageReader imageReader = ImageReader.open(file.getPath());

		final long lastModified = file.lastModified();

		myEntries = new THashMap<String, ArchiveEntry>(imageReader.getAttributeOffsetsLength());
		ImageReader.Node node = imageReader.findNode(UTF8String.MODULES_STRING);
		if(node instanceof ImageReader.Directory)
		{
			((ImageReader.Directory) node).walk(new Consumer<ImageReader.Node>()
			{
				@Override
				public void accept(ImageReader.Node value)
				{
					if(value instanceof ImageReader.Directory)
					{
						imageReader.findNode(value.getName());

						((ImageReader.Directory) value).walkChildren(this);

						add(new JImageDirectoryArchiveEntry(cutStartSlash(value.getNameString()), lastModified));
					}
					else if(value instanceof ImageReader.Resource)
					{
						add(new JImageFileArchiveEntry(imageReader, (ImageReader.Resource) value, lastModified));
					}
				}
			});
		}
	}

	public static String cutStartSlash(String name)
	{
		if(name.charAt(0) == '/')
		{
			return name.substring(1, name.length());
		}
		return name;
	}

	private void add(@NotNull ArchiveEntry archiveEntry)
	{
		myEntries.put(archiveEntry.getName(), archiveEntry);
	}

	@NotNull
	@Override
	public String getName()
	{
		return myName;
	}

	@Nullable
	@Override
	public ArchiveEntry getEntry(String name)
	{
		return myEntries.get(name);
	}

	@Nullable
	@Override
	public InputStream getInputStream(@NotNull ArchiveEntry entry) throws IOException
	{
		if(entry.isDirectory())
		{
			return new ByteArrayInputStream(ArrayUtil.EMPTY_BYTE_ARRAY);
		}
		if(entry instanceof JImageFileArchiveEntry)
		{
			return new ByteArrayInputStream(((JImageFileArchiveEntry) entry).getBytes());
		}
		return null;
	}

	@NotNull
	@Override
	public Iterator<? extends ArchiveEntry> entries()
	{
		return myEntries.values().iterator();
	}

	@Override
	public int getSize()
	{
		return myEntries.size();
	}
}
