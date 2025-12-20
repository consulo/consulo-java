package com.intellij.psi.impl.cache.impl;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiFile;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

/**
 * @author max
 */
public abstract class SCR20733Test extends PsiTestCase
{
	private static final Logger LOGGER = Logger.getInstance(SCR20733Test.class);

	private VirtualFile myPrjDir1;
	private VirtualFile mySrcDir1;
	private VirtualFile myPackDir;

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();

		final File root = FileUtil.createTempFile(getTestName(false), "");
		root.delete();
		root.mkdir();
		myFilesToDelete.add(root);

		ApplicationManager.getApplication().runWriteAction(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					VirtualFile rootVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(root.getAbsolutePath().replace(File.separatorChar, '/'));

					myPrjDir1 = rootVFile.createChildDirectory(null, "prj1");
					mySrcDir1 = myPrjDir1.createChildDirectory(null, "src1");

					myPackDir = mySrcDir1.createChildDirectory(null, "p");
					VirtualFile file1 = myPackDir.createChildData(null, "A.java");
					VfsUtil.saveText(file1, "package p; public class A{ public void foo(); }");

					PsiTestUtil.addContentRoot(myModule, myPrjDir1);
					PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
				}
				catch(IOException e)
				{
					LOGGER.error(e);
				}
			}
		});
	}

	public void testBug()
	{
		ApplicationManager.getApplication().runWriteAction(new Runnable()
		{
			public void run()
			{
				PsiClass psiClass = myJavaFacade.findClass("p.A");
				assertEquals("p.A", psiClass.getQualifiedName());

				PsiFile psiFile = myPsiManager.findFile(myPackDir.findChild("A.java"));
				psiFile.getChildren();
				assertEquals(psiFile, psiClass.getContainingFile());

				VirtualFile file = psiFile.getVirtualFile();
				assertEquals(myModule, ModuleUtil.findModuleForFile(file, myProject));

				Module anotherModule = createModule("another");

				PsiTestUtil.addSourceRoot(anotherModule, mySrcDir1);

				assertEquals(anotherModule, ModuleUtil.findModuleForFile(file, myProject));
			}
		});
	}
}
