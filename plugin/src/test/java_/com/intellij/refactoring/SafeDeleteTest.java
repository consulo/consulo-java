package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.editor.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.testFramework.PlatformTestUtil;
import consulo.util.collection.ContainerUtil;
import consulo.language.editor.TargetElementUtil;
import consulo.codeInsight.TargetElementUtilEx;
import org.jetbrains.annotations.NonNls;

import java.io.File;

import static org.junit.Assert.*;

public abstract class SafeDeleteTest extends MultiFileTestCase
{
	private VirtualFile myRootBefore;

	@Override
	protected String getTestDataPath()
	{
		return JavaTestUtil.getJavaTestDataPath();
	}

	@Override
	protected String getTestRoot()
	{
		return "/refactoring/safeDelete/";
	}

	@Override
	protected boolean clearModelBeforeConfiguring()
	{
		return true;
	}

	public void testImplicitCtrCall() throws Exception
	{
		try
		{
			doTest("Super");
			fail();
		}
		catch(BaseRefactoringProcessor.ConflictsInTestsException e)
		{
			String message = e.getMessage();
			assertTrue(message, message.startsWith("constructor <b><code>Super.Super()</code></b> has 1 usage that is not safe to delete"));
		}
	}

	public void testImplicitCtrCall2() throws Exception
	{
		try
		{
			doTest("Super");
			fail();
		}
		catch(BaseRefactoringProcessor.ConflictsInTestsException e)
		{
			String message = e.getMessage();
			assertTrue(message, message.startsWith("constructor <b><code>Super.Super()</code></b> has 1 usage that is not safe to delete"));
		}
	}

	public void testMultipleInterfacesImplementation() throws Exception
	{
		myDoCompare = false;
		doTest("IFoo");
	}

	public void testMultipleInterfacesImplementationThroughCommonInterface() throws Exception
	{
		myDoCompare = false;
		doTest("IFoo");
	}

	public void testUsageInExtendsList() throws Exception
	{
		doSingleFileTest();
	}

	public void testParameterInHierarchy() throws Exception
	{
		myDoCompare = false;
		doTest("C2");
	}


	public void testTopLevelDocComment() throws Exception
	{
		myDoCompare = false;
		doTest("foo.C1");
	}

	public void testTopParameterInHierarchy() throws Exception
	{
		myDoCompare = false;
		doTest("I");
	}

	public void testExtendsList() throws Exception
	{
		myDoCompare = false;
		doTest("B");
	}

	public void testJavadocParamRef() throws Exception
	{
		myDoCompare = false;
		doTest("Super");
	}

	public void testEnumConstructorParameter() throws Exception
	{
		myDoCompare = false;
		doTest("UserFlags");
	}

	public void testSafeDeleteStaticImports() throws Exception
	{
		myDoCompare = false;
		doTest("A");
	}

	public void testRemoveOverridersInspiteOfUnsafeUsages() throws Exception
	{
		myDoCompare = false;
		BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest("A"));
	}

	public void testLocalVariable() throws Exception
	{
		myDoCompare = false;
		doTest("Super");
	}

	public void testMethodDeepHierarchy() throws Exception
	{
		myDoCompare = false;
		doTest("Super");
	}

	public void testLocalVariableSideEffect() throws Exception
	{
		myDoCompare = false;
		try
		{
			doTest("Super");
			fail("Side effect was ignored");
		}
		catch(BaseRefactoringProcessor.ConflictsInTestsException e)
		{
			String message = e.getMessage();
			assertTrue(message, message.startsWith("local variable <b><code>varName</code></b> has 1 usage that is not safe to delete.\n" +
					"Of those 0 usages are in strings, comments, or non-code files."));
		}
	}

	public void testLastResourceVariable() throws Exception
	{
		//LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
		doSingleFileTest();
	}

	public void testLastTypeParam() throws Exception
	{
		//LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
		doSingleFileTest();
	}

	public void testTypeParamFromDiamond() throws Exception
	{
		//LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
		doSingleFileTest();
	}

	public void testStripOverride() throws Exception
	{
		doSingleFileTest();
	}

	private void doTest(@NonNls final String qClassName) throws Exception
	{
		doTest(new PerformAction()
		{
			@Override
			public void performAction(VirtualFile rootDir, VirtualFile rootAfter) throws Exception
			{
				SafeDeleteTest.this.performAction(qClassName);
				PlatformTestUtil.assertDirectoriesEqual(rootAfter, myRootBefore);
			}
		});
	}

	private void doSingleFileTest() throws Exception
	{
		configureByFile(getTestRoot() + getTestName(false) + ".java");
		performAction();
		checkResultByFile(getTestRoot() + getTestName(false) + "_after.java");
	}

	private void performAction(final String qClassName) throws Exception
	{
		final PsiClass aClass = myJavaFacade.findClass(qClassName, GlobalSearchScope.allScope(getProject()));
		assertNotNull("Class " + qClassName + " not found", aClass);

		final String root = ProjectRootManager.getInstance(getProject()).getContentRoots()[0].getPath();
		myRootBefore = configureByFiles(new File(root), aClass.getContainingFile().getVirtualFile());

		performAction();
	}

	private void performAction()
	{
		final PsiElement psiElement = TargetElementUtil
				.findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED, TargetElementUtilEx.REFERENCED_ELEMENT_ACCEPTED));
		assertNotNull("No element found in text:\n" + getFile().getText(), psiElement);
		SafeDeleteHandler.invoke(getProject(), new PsiElement[]{psiElement}, true);
	}
}