/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.compiled;

import static com.intellij.java.language.psi.compiled.ClassFileDecompilers.Full;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;

import consulo.logging.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.java.language.psi.compiled.ClassFileDecompilers;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.java.language.util.cls.ClsFormatException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileContent;

/**
 * @author max
 */
public class ClassFileStubBuilder implements BinaryFileStubBuilder
{
	private static final Logger LOG = Logger.getInstance(ClassFileStubBuilder.class);

	public static final int STUB_VERSION = 20;

	@Override
	public boolean acceptsFile(@Nonnull VirtualFile file)
	{
		return true;
	}

	@Override
	public StubElement buildStubTree(@Nonnull FileContent fileContent)
	{
		VirtualFile file = fileContent.getFile();
		byte[] content = fileContent.getContent();

		try
		{
			try
			{
				file.setPreloadedContentHint(content);
				ClassFileDecompilers.Decompiler decompiler = ClassFileDecompilers.find(file);
				if(decompiler instanceof Full)
				{
					return ((Full) decompiler).getStubBuilder().buildFileStub(fileContent);
				}
			}
			catch(ClsFormatException e)
			{
				if(LOG.isDebugEnabled())
				{
					LOG.debug(file.getPath(), e);
				}
				else
				{
					LOG.info(file.getPath() + ": " + e.getMessage());
				}
			}

			try
			{
				PsiFileStub<?> stub = ClsFileImpl.buildFileStub(file, content);
				if(stub == null && fileContent.getFileName().indexOf('$') < 0)
				{
					LOG.info("No stub built for file " + fileContent);
				}
				return stub;
			}
			catch(ClsFormatException e)
			{
				if(LOG.isDebugEnabled())
				{
					LOG.debug(file.getPath(), e);
				}
				else
				{
					LOG.info(file.getPath() + ": " + e.getMessage());
				}
			}
		}
		finally
		{
			file.setPreloadedContentHint(null);
		}

		return null;
	}

	private static final Comparator<Object> CLASS_NAME_COMPARATOR = (o1, o2) -> o1.getClass().getName().compareTo(o2.getClass().getName());

	@Override
	public int getStubVersion()
	{
		int version = STUB_VERSION;

		List<ClassFileDecompilers.Decompiler> decompilers = ContainerUtil.newArrayList(ClassFileDecompilers.EP_NAME.getExtensions());
		Collections.sort(decompilers, CLASS_NAME_COMPARATOR);
		for(ClassFileDecompilers.Decompiler decompiler : decompilers)
		{
			if(decompiler instanceof Full)
			{
				version = version * 31 + ((Full) decompiler).getStubBuilder().getStubVersion() + decompiler.getClass().getName().hashCode();
			}
		}

		return version;
	}
}