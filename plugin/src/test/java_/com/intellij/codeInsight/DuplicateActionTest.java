package com.intellij.codeInsight;

import org.jetbrains.annotations.NonNls;
import consulo.ui.ex.action.IdeActions;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public abstract class DuplicateActionTest extends LightCodeInsightFixtureTestCase
{
	public void testOneLine()
	{
		doTest("xxx<caret>\n", "txt", "xxx\nxxx<caret>\n");
	}

	public void testEmpty()
	{
		doTest("<caret>", "txt", "<caret>");
	}

	private void doTest(String before, @NonNls String ext, String after)
	{
		myFixture.configureByText("a." + ext, before);
		myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
		myFixture.checkResult(after);
	}

	public void testSelectName()
	{
		doTest("\nclass C {\n  void foo() {}<caret>\n}\n", "java", "\nclass C {\n  void foo() {}\n  void <caret>foo() {}\n}\n");
	}

	public void testPreserveCaretPositionWhenItsAlreadyInsideElementsName()
	{
		doTest("\nclass C {\n  void fo<caret>o() {}\n}\n", "java", "\nclass C {\n  void foo() {}\n  void fo<caret>o() {}\n}\n");
	}

	public void testXmlTag()
	{
		doTest("\n<root>\n  <foo/><caret>\n</root>\n", "xml", "\n<root>\n  <foo/>\n  <foo/><caret>\n</root>\n");
	}
}
