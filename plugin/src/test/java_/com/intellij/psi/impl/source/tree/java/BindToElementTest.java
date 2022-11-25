package com.intellij.psi.impl.source.tree.java;

import static org.junit.Assert.assertNotNull;

import java.io.File;

import com.intellij.codeInsight.CodeInsightTestCase;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiTypeElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.testFramework.PsiTestUtil;
import consulo.language.util.IncorrectOperationException;
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
