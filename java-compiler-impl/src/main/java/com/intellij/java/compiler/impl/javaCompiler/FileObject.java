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
package com.intellij.java.compiler.impl.javaCompiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.annotation.Nonnull;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.util.collection.ArrayUtil;

/**
 * @author cdr
 */
public class FileObject
{
	private static final byte[] NOT_LOADED = ArrayUtil.EMPTY_BYTE_ARRAY;

	private final File myFile;
	private byte[] myContent;
	private int myClassId = -1;

	public FileObject(@Nonnull File file, @Nonnull byte[] content)
	{
		myFile = file;
		myContent = content;
	}

	public FileObject(File file)
	{
		myFile = file;
		myContent = NOT_LOADED;
	}

	public void setClassId(int classId)
	{
		myClassId = classId;
	}

	public int getClassId()
	{
		return myClassId;
	}

	public File getFile()
	{
		return myFile;
	}

	public byte[] getOrLoadContent() throws IOException
	{
		if(myContent == NOT_LOADED)
		{
			return FileUtil.loadFileBytes(myFile);
		}
		return myContent;
	}

	public boolean save(byte[] content) throws IOException
	{
		myContent = content;

		try
		{
			FileUtil.writeToFile(myFile, myContent);
		}
		catch(FileNotFoundException e)
		{
			FileUtil.createParentDirs(myFile);
			FileUtil.writeToFile(myFile, myContent);
		}
		return true;
	}

	@Override
	public String toString()
	{
		return getFile().toString();
	}
}
