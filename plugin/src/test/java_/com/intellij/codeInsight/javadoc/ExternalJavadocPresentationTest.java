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
package com.intellij.codeInsight.javadoc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import com.intellij.java.impl.codeInsight.javadoc.JavaDocExternalFilter;
import consulo.virtualFileSystem.LocalFileSystem;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;
import com.intellij.JavaTestUtil;
import consulo.language.impl.internal.psi.LoadTextUtil;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * User: anna
 * Date: 12/7/11
 */
public abstract class ExternalJavadocPresentationTest extends LightCodeInsightTestCase
{
	private static final String TEST_ROOT = "/codeInsight/externalJavadoc/";

	@Nonnull
	@Override
	protected String getTestDataPath()
	{
		return JavaTestUtil.getJavaTestDataPath();
	}

	public void testStringClass() throws Exception
	{
		doTest("", "String/7/page.html", "String/7/expected.html");
		doTest("", "String/6/page.html", "String/6/expected.html");
	}

	public void testToLowerCase() throws Exception
	{
		doTest("lang/String.html#toLowerCase()", "String/7/page.html", "String/7/expectedToLowerCase.html");
		doTest("lang/String.html#toLowerCase()", "String/6/page.html", "String/6/expectedToLowerCase.html");
	}

	public void testPackageSummary() throws Exception
	{
		doTest("java/lang/package-summary.html", "packageSummary/util/page.html", "packageSummary/util/expected.html");
	}

	private void doTest(@NonNls String url, @NonNls String pageText, @NonNls String expected) throws Exception
	{
		final String basePath = getTestDataPath() + TEST_ROOT;
		final VirtualFile pageTextFile = LocalFileSystem.getInstance().findFileByPath(basePath + pageText);
		assertNotNull(pageTextFile);

		final VirtualFile expectedTextFile = LocalFileSystem.getInstance().findFileByPath(basePath + expected);
		assertNotNull(expectedTextFile);

		class JavadocExternalTestFilter extends JavaDocExternalFilter
		{

			public JavadocExternalTestFilter(Project project)
			{
				super(project);
			}

			@Override
			public void doBuildFromStream(String url, Reader input, StringBuilder data, boolean searchForEncoding, boolean matchStart) throws IOException
			{
				super.doBuildFromStream(url, input, data, searchForEncoding, matchStart);
			}
		}
		JavadocExternalTestFilter filter = new JavadocExternalTestFilter(getProject());
		final StringBuilder extractedData = new StringBuilder();
		filter.doBuildFromStream(url, new StringReader(LoadTextUtil.loadText(pageTextFile).toString()), extractedData, false, false);
		assertEquals(LoadTextUtil.loadText(expectedTextFile).toString(), extractedData.toString());
	}
}
