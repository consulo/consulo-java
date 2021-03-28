package com.intellij.psi.impl.source.tree.java;

import static org.junit.Assert.assertNotNull;

import java.io.File;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.IncorrectOperationException;
import consulo.logging.Logger;

/**
 * @author dsl
 */
public abstract class BindToElementTest extends CodeInsightTestCase
{
	private static final Logger LOGGER = Logger.getInstance(BindToElementTest.class);

	public static final String TEST_ROOT = "/psi/impl/bindToElementTest/".replace('/', File.separatorChar);

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		VirtualFile root = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>()
		{
			@Override
			public VirtualFile compute()
			{
				return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(new File(TEST_ROOT), "prj"));
			}
		});
		assertNotNull(root);
		PsiTestUtil.addSourceRoot(myModule, root);
	}

	public void testSingleClassImport() throws Exception
	{
		doTest(new Runnable()
		{
			@Override
			public void run()
			{
				PsiElement element = myFile.findElementAt(myEditor.getCaretModel().getOffset());
				final PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getParentOfType(element, PsiJavaCodeReferenceElement.class);
				final PsiClass aClassA = JavaPsiFacade.getInstance(myProject).findClass("p2.A", GlobalSearchScope.moduleScope(myModule));
				assertNotNull(aClassA);
				try
				{
					referenceElement.bindToElement(aClassA);
				}
				catch(IncorrectOperationException e)
				{
					LOGGER.error(e);
				}
			}
		});
	}

	public void testReplacingType() throws Exception
	{
		doTest(new Runnable()
		{
			@Override
			public void run()
			{
				final PsiElement elementAt = myFile.findElementAt(myEditor.getCaretModel().getOffset());
				final PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(elementAt, PsiTypeElement.class);
				final PsiClass aClassA = JavaPsiFacade.getInstance(myProject).findClass("p2.A", GlobalSearchScope.moduleScope(myModule));
				assertNotNull(aClassA);
				final PsiElementFactory factory = myJavaFacade.getElementFactory();
				final PsiClassType type = factory.createType(aClassA);
				try
				{
					typeElement.replace(factory.createTypeElement(type));
				}
				catch(IncorrectOperationException e)
				{
					LOGGER.error(e);
				}
			}
		});
	}

	private void doTest(final Runnable runnable) throws Exception
	{
		final String relativeFilePath = "/psi/impl/bindToElementTest/" + getTestName(false);
		configureByFile(relativeFilePath + ".java");
		runnable.run();
		checkResultByFile(relativeFilePath + ".java.after");
	}
}
