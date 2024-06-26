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
package com.intellij.java.compiler.impl.javaCompiler.javac;

import com.intellij.java.compiler.impl.OutputParser;
import com.intellij.java.compiler.impl.javaCompiler.FileObject;
import com.intellij.java.language.impl.JavaClassFileType;
import com.intellij.java.language.impl.JavaFileType;
import consulo.compiler.localize.CompilerLocalize;
import consulo.logging.Logger;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Eugene Zhuravlev
 *         Date: Sep 14, 2005
 */
public class FilePathActionJavac extends JavacParserAction
{
	private static final Logger LOG = Logger.getInstance(FilePathActionJavac.class);
	private static final Pattern ourPattern = Pattern.compile("^\\w+\\[(.+)\\]$", Pattern.CASE_INSENSITIVE);

	private final Matcher myJdk7FormatMatcher;

	public FilePathActionJavac(final Matcher matcher)
	{
		super(matcher);
		myJdk7FormatMatcher = ourPattern.matcher("");
	}

	@Override
	protected void doExecute(final String line, final String originalPath, final OutputParser.Callback callback)
	{
		if (LOG.isDebugEnabled())
		{
			LOG.debug("Process parsing message: " + originalPath);
		}

		String filePath = originalPath;
		// for jdk7: cut off characters wrapping the path. e.g. "RegularFileObject[C:/tmp/bugs/src/a/Demo1.java]"
		if (myJdk7FormatMatcher.reset(filePath).matches())
		{
			filePath = myJdk7FormatMatcher.group(1);
		}

		// jdk 9 specific
		// C:\\Users\\VISTALL\\Documents\\Consulo\\untitled71\\out\\production\\untitled71:org\\example\\Main.class
		int i = filePath.lastIndexOf(':');
		if (i != -1)
		{
			// check next character - if it slash, it's not module separator
			char next = filePath.charAt(i + 1);
			if (next != '\\' && next != '/')
			{
				char[] chars = filePath.toCharArray();
				chars[i] = '/';
				filePath = FileUtil.toSystemIndependentName(new String(chars));
			}
		}

		int index = filePath.lastIndexOf('/');
		final String name = index >= 0 ? filePath.substring(index + 1) : filePath;

		CharSequence extension = FileUtil.getExtension((CharSequence) name);
		if (Comparing.equal(extension, JavaFileType.INSTANCE.getDefaultExtension()))
		{
			callback.fileProcessed(filePath);
			callback.setProgressText(CompilerLocalize.progressParsingFile(name).get());
		}
		else if (Comparing.equal(extension, JavaClassFileType.INSTANCE.getDefaultExtension()))
		{
			callback.fileGenerated(new FileObject(new File(filePath)));
		}
	}
}
