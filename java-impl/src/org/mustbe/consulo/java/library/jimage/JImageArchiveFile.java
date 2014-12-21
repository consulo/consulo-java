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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ArchiveEntry;
import com.intellij.openapi.vfs.ArchiveFile;
import com.intellij.util.ArrayUtil;
import consulo.internal.jdk.internal.jimage.ImageReader;

/**
 * @author VISTALL
 * @since 21.12.14
 */
public class JImageArchiveFile implements ArchiveFile
{
	private final Map<String, ArchiveEntry> myEntries;

	public JImageArchiveFile(String basePath) throws IOException
	{
		ImageReader imageReader = ImageReader.open(basePath);

		File file = new File(basePath);

		long lastModified = file.lastModified();

		String[] entryNames = imageReader.getEntryNames(false);

		myEntries = new TreeMap<String, ArchiveEntry>();
		for(String entryName : entryNames)
		{
			fillDirectory(entryName, lastModified, myEntries);

			myEntries.put(entryName, new JImageFileArchiveEntry(imageReader, entryName, lastModified));
		}
	}

	private static void fillDirectory(String entryName, long lastModified, Map<String, ArchiveEntry> entries)
	{
		String dirName = StringUtil.getPackageName(entryName, '/');
		if(dirName.isEmpty())
		{
			return;
		}

		if(entries.containsKey(dirName))
		{
			return;
		}

		entries.put(dirName, new JImageDirectoryArchiveEntry(dirName + "/", lastModified));
	}

	@Nullable
	@Override
	public ArchiveEntry getEntry(String name)
	{
		return myEntries.get(name);
	}

	@Nullable
	@Override
	public InputStream getInputStream(ArchiveEntry entry) throws IOException
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
