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
package com.intellij.testFramework;

import consulo.application.WriteAction;
import consulo.document.FileDocumentManager;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.ide.impl.idea.openapi.roots.ModuleRootModificationUtil;
import consulo.content.OrderRootType;
import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.jdom.JDOMUtil;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.psi.*;
import consulo.language.impl.internal.psi.PsiManagerImpl;
import consulo.language.util.IncorrectOperationException;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author Mike
 */
public abstract class PsiTestCase extends ModuleTestCase
{
	protected PsiManagerImpl myPsiManager;
	protected PsiFile myFile;
	protected PsiTestData myTestDataBefore;
	protected PsiTestData myTestDataAfter;
	private String myDataRoot;

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		myPsiManager = (PsiManagerImpl) PsiManager.getInstance(myProject);
	}

	@Override
	protected void tearDown() throws Exception
	{
		myPsiManager = null;
		myFile = null;
		myTestDataBefore = null;
		myTestDataAfter = null;
		super.tearDown();
	}

	protected PsiFile createDummyFile(String fileName, String text) throws IncorrectOperationException
	{
		FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);
		return PsiFileFactory.getInstance(myProject).createFileFromText(fileName, type, text);
	}

	protected PsiFile createFile(@NonNls String fileName, String text) throws Exception
	{
		return createFile(myModule, fileName, text);
	}

	protected PsiFile createFile(Module module, String fileName, String text) throws Exception
	{
		File dir = createTempDirectory();
		VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));

		return createFile(module, vDir, fileName, text);
	}

	protected PsiFile createFile(final Module module, final VirtualFile vDir, final String fileName, final String text) throws IOException
	{
		return WriteAction.compute(() -> {
			if(!ModuleRootManager.getInstance(module).getFileIndex().isInSourceContent(vDir))
			{
				addSourceContentToRoots(module, vDir);
			}

			final VirtualFile vFile = vDir.createChildData(vDir, fileName);
			VfsUtil.saveText(vFile, text);
			Assert.assertNotNull(vFile);
			final PsiFile file = myPsiManager.findFile(vFile);
			Assert.assertNotNull(file);
			return file;
		});
	}

	protected void addSourceContentToRoots(final Module module, final VirtualFile vDir)
	{
		PsiTestUtil.addSourceContentToRoots(module, vDir);
	}

	protected PsiElement configureByFileWithMarker(String filePath, String marker) throws Exception
	{
		final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(filePath.replace(File.separatorChar, '/'));
		Assert.assertNotNull("file " + filePath + " not found", vFile);

		String fileText = VfsUtil.loadText(vFile);
		fileText = StringUtil.convertLineSeparators(fileText);

		int offset = fileText.indexOf(marker);
		Assert.assertTrue(offset >= 0);
		fileText = fileText.substring(0, offset) + fileText.substring(offset + marker.length());

		myFile = createFile(vFile.getName(), fileText);

		return myFile.findElementAt(offset);
	}

	protected void configure(String path, String dataName) throws Exception
	{
		myDataRoot = getTestDataPath() + path;

		myTestDataBefore = loadData(dataName);

		PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
		VirtualFile vDir = PsiTestUtil.createTestProjectStructure(myProject, myModule, myDataRoot, myFilesToDelete);

		final VirtualFile vFile = vDir.findChild(myTestDataBefore.getTextFile());
		myFile = myPsiManager.findFile(vFile);
	}

	protected String getTestDataPath()
	{
		return "/";
	}

	protected String loadFile(String name) throws Exception
	{
		String result = FileUtil.loadFile(new File(getTestDataPath() + File.separatorChar + name));
		return StringUtil.convertLineSeparators(result);
	}

	private PsiTestData loadData(String dataName) throws Exception
	{
		Document document = JDOMUtil.loadDocument(new File(myDataRoot + "/" + "data.xml"));

		PsiTestData data = createData();
		Element documentElement = document.getRootElement();

		final List nodes = documentElement.getChildren("data");

		for(Object node1 : nodes)
		{
			Element node = (Element) node1;
			String value = node.getAttributeValue("name");

			if(value.equals(dataName))
			{
				DefaultJDOMExternalizer.readExternal(data, node);
				data.loadText(myDataRoot);

				return data;
			}
		}

		throw new IllegalArgumentException("Cannot find data chunk '" + dataName + "'");
	}

	protected PsiTestData createData()
	{
		return new PsiTestData();
	}

	protected void checkResult(String dataName) throws Exception
	{
		myTestDataAfter = loadData(dataName);

		final String textExpected = myTestDataAfter.getText();
		final String actualText = myFile.getText();

		if(!textExpected.equals(actualText))
		{
			System.out.println("Text mismatch: " + getTestName(false) + "(" + getClass().getName() + ")");
			System.out.println("Text expected:");
			printText(textExpected);
			System.out.println("Text found:");
			printText(actualText);

			Assert.fail("text");
		}

		//    assertEquals(myTestDataAfter.getText(), myFile.getText());
	}

	protected static void printText(String text)
	{
		final String q = "\"";
		System.out.print(q);

		text = StringUtil.convertLineSeparators(text);

		StringTokenizer tokenizer = new StringTokenizer(text, "\n", true);
		while(tokenizer.hasMoreTokens())
		{
			final String token = tokenizer.nextToken();

			if(token.equals("\n"))
			{
				System.out.print(q);
				System.out.println();
				System.out.print(q);
				continue;
			}

			System.out.print(token);
		}

		System.out.print(q);
		System.out.println();
	}

	protected void addLibraryToRoots(final VirtualFile jarFile, OrderRootType rootType)
	{
		addLibraryToRoots(myModule, jarFile, rootType);
	}

	protected static void addLibraryToRoots(final Module module, final VirtualFile root, final OrderRootType rootType)
	{
		Assert.assertEquals(OrderRootType.CLASSES, rootType);
		ModuleRootModificationUtil.addModuleLibrary(module, root.getUrl());
	}


	public PsiFile getFile()
	{
		return myFile;
	}

	public com.intellij.openapi.editor.Document getDocument(PsiFile file)
	{
		return PsiDocumentManager.getInstance(getProject()).getDocument(file);
	}

	public com.intellij.openapi.editor.Document getDocument(VirtualFile file)
	{
		return FileDocumentManager.getInstance().getDocument(file);
	}

	public void commitDocument(com.intellij.openapi.editor.Document document)
	{
		PsiDocumentManager.getInstance(getProject()).commitDocument(document);
	}
}
