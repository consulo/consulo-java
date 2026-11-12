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

import consulo.util.collection.ArrayUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author cdr
 */
/**
 * A class file written by the compiler - of the machine of the JDK, read and written through its path.
 */
public class FileObject
{
	private static final byte[] NOT_LOADED = ArrayUtil.EMPTY_BYTE_ARRAY;

	private final Path myPath;
	private byte[] myContent;
	private int myClassId = -1;

	public FileObject(Path path, byte[] content)
	{
		myPath = path;
		myContent = content;
	}

	public FileObject(Path path)
	{
		myPath = path;
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

	public Path getPath()
	{
		return myPath;
	}

	/**
	 * The path as a file name - a key of the parsing info; for a file of another machine it names its path there.
	 */
	public File getFile()
	{
		return new File(myPath.toString());
	}

	public byte[] getOrLoadContent() throws IOException
	{
		if(myContent == NOT_LOADED)
		{
			return Files.readAllBytes(myPath);
		}
		return myContent;
	}

	public boolean save(byte[] content) throws IOException
	{
		myContent = content;

		Path parent = myPath.getParent();
		if(parent != null)
		{
			Files.createDirectories(parent);
		}
		Files.write(myPath, myContent);
		return true;
	}

	@Override
	public String toString()
	{
		return myPath.toString();
	}
}
