package com.intellij.codeInsight;

import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.EditorActionManager;
import consulo.dataContext.DataManager;
import consulo.ui.ex.action.IdeActions;
import consulo.ide.impl.idea.openapi.editor.impl.TrailingSpacesStripper;
import com.intellij.testFramework.PlatformTestCase;

/**
 * @author mike
 */
@PlatformTestCase.WrapInCommand
public abstract class GenerateJavadocTest extends CodeInsightTestCase {
  public void test1() throws Exception { doTest(); }
  public void test2() throws Exception { doTest(); }
  public void test3() throws Exception { doTest(); }
  public void testIdeadev2328() throws Exception { doTest(); }
  public void testIdeadev2328_2() throws Exception { doTest(); }

  private void doTest() throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/generateJavadoc/before" + name + ".java");
    performAction();
    checkResultByFile("/codeInsight/generateJavadoc/after" + name + ".java", true);
  }

  private void performAction() {
    EditorActionManager actionManager = EditorActionManager.getInstance();
    EditorActionHandler actionHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    actionHandler.execute(myEditor, DataManager.getInstance().getDataContext());
    TrailingSpacesStripper.strip(myEditor.getDocument(), false, false);
  }
}
