package com.intellij.refactoring.inline;

import static org.junit.Assert.assertTrue;

import jakarta.annotation.Nonnull;

import com.intellij.java.impl.refactoring.inline.InlineConstantFieldProcessor;
import org.jetbrains.annotations.NonNls;
import com.intellij.JavaTestUtil;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import consulo.language.psi.PsiReference;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.refactoring.LightRefactoringTestCase;
import consulo.util.collection.ContainerUtil;
import consulo.language.editor.TargetElementUtil;
import consulo.codeInsight.TargetElementUtilEx;

public abstract class InlineConstantFieldTest extends LightRefactoringTestCase
{
	@Nonnull
	@Override
	protected String getTestDataPath()
	{
		return JavaTestUtil.getJavaTestDataPath();
	}

	public void testQualifiedExpression() throws Exception
	{
		doTest();
	}

	public void testQualifiedConstantExpression() throws Exception
	{
		doTest();
	}

	public void testQualifiedConstantExpressionReplacedWithAnotherOne() throws Exception
	{
		doTest();
	}

	public void testStaticallyImportedQualifiedExpression() throws Exception
	{
		doTest();
	}

	public void testCastWhenLambdaAsQualifier() throws Exception
	{
		doTest();
	}

	public void testConstantFromLibrary() throws Exception
	{
		doTest();
	}

	private void doTest() throws Exception
	{
		String name = getTestName(false);
		@NonNls String fileName = "/refactoring/inlineConstantField/" + name + ".java";
		configureByFile(fileName);
		performAction();
		checkResultByFile(fileName + ".after");
	}

	private void performAction()
	{
		PsiElement element = TargetElementUtil.findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED, TargetElementUtilEx.REFERENCED_ELEMENT_ACCEPTED));
		final PsiReference ref = myFile.findReferenceAt(myEditor.getCaretModel().getOffset());
		PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression) ref : null;
		assertTrue(element instanceof PsiField);
		PsiField field = (PsiField) element.getNavigationElement();
		new InlineConstantFieldProcessor(field, getProject(), refExpr, element instanceof PsiCompiledElement).run();
	}
}