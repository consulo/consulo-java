/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.lang.java.parser;

import java.io.IOException;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.impl.parser.JavaParserUtil;
import org.jetbrains.annotations.NonNls;
import com.intellij.java.language.impl.JavaFileType;
import consulo.language.ast.ASTNode;
import consulo.language.parser.PsiBuilder;
import com.intellij.java.language.JavaLanguage;
import consulo.virtualFileSystem.fileType.FileType;
import com.intellij.java.language.LanguageLevel;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.impl.file.SingleRootFileViewProvider;
import com.intellij.java.language.impl.psi.impl.source.PsiJavaFileImpl;
import consulo.language.impl.ast.FileElement;
import consulo.language.ast.IFileElementType;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.version.LanguageVersion;
import consulo.testFramework.ParsingTestCase;

public abstract class JavaParsingTestCase extends ParsingTestCase
{
	private LanguageLevel myLanguageLevel;

	@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
	public JavaParsingTestCase(@NonNls final String dataPath)
	{
		super("psi/" + dataPath, "java");
	}

	@Override
	protected String getTestDataPath()
	{
		return "/testData";
	}

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		myLanguageLevel = LanguageLevel.JDK_1_6;
	}

	@Nonnull
	@Override
	public LanguageVersion resolveLanguageVersion(@jakarta.annotation.Nonnull FileType fileType)
	{
		return myLanguageLevel.toLangVersion();
	}

	@Override
	protected void tearDown() throws Exception
	{
		super.tearDown();
	}

	protected interface TestParser
	{
		void parse(PsiBuilder builder);
	}

	protected void doParserTest(final String text, final TestParser parser)
	{
		final String name = getTestName(false);
		myFile = createPsiFile(name, text, parser);
		myFile.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, myLanguageLevel);
		try
		{
			checkResult(name, myFile);
		}
		catch(IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	private static IFileElementType TEST_FILE_ELEMENT_TYPE = null;
	private static TestParser TEST_PARSER;

	private PsiFile createPsiFile(final String name, final String text, final TestParser parser)
	{
		if(TEST_FILE_ELEMENT_TYPE == null)
		{
			TEST_FILE_ELEMENT_TYPE = new MyIFileElementType();
		}

		TEST_PARSER = parser;

		final LightVirtualFile virtualFile = new LightVirtualFile(name + JavaFileType.DOT_DEFAULT_EXTENSION, JavaFileType.INSTANCE, text, -1);
		final FileViewProvider viewProvider = new SingleRootFileViewProvider(PsiManager.getInstance(myProject), virtualFile, true);
		return new PsiJavaFileImpl(viewProvider)
		{
			@Nonnull
			@Override
			protected FileElement createFileElement(final CharSequence text)
			{
				return new FileElement(TEST_FILE_ELEMENT_TYPE, text);
			}
		};
	}

	private static PsiBuilder createBuilder(final ASTNode chameleon)
	{
		final PsiBuilder builder = JavaParserUtil.createBuilder(chameleon);
		builder.setDebugMode(true);
		return builder;
	}

	private static class MyIFileElementType extends IFileElementType
	{
		public MyIFileElementType()
		{
			super("test.java.file", JavaLanguage.INSTANCE);
		}

		@Override
		public ASTNode parseContents(final ASTNode chameleon)
		{
			final PsiBuilder builder = createBuilder(chameleon);

			final PsiBuilder.Marker root = builder.mark();
			TEST_PARSER.parse(builder);
			if(!builder.eof())
			{
				final PsiBuilder.Marker unparsed = builder.mark();
				while(!builder.eof())
				{
					builder.advanceLexer();
				}
				unparsed.error("Unparsed tokens");
			}
			root.done(this);

			final ASTNode rootNode = builder.getTreeBuilt();
			return rootNode.getFirstChildNode();
		}
	}
}
