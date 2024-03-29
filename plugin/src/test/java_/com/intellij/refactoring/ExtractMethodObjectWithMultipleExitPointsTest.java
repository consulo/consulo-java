/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring;

import jakarta.annotation.Nonnull;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import consulo.application.ApplicationManager;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.impl.refactoring.extractMethodObject.ExtractMethodObjectHandler;
import com.intellij.java.impl.refactoring.extractMethodObject.ExtractMethodObjectProcessor;

public abstract class ExtractMethodObjectWithMultipleExitPointsTest extends LightRefactoringTestCase {
  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(final boolean createInnerClass) throws Exception {
    final String testName = getTestName(false);
    configureByFile("/refactoring/extractMethodObject/multipleExitPoints/" + testName + ".java");
    int startOffset = myEditor.getSelectionModel().getSelectionStart();
    int endOffset = myEditor.getSelectionModel().getSelectionEnd();

    final PsiElement[] elements;
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(myFile, startOffset, endOffset);
    if (expr != null) {
      elements = new PsiElement[]{expr};
    }
    else {
      elements = CodeInsightUtil.findStatementsInRange(myFile, startOffset, endOffset);
    }

    final ExtractMethodObjectProcessor processor =
      new ExtractMethodObjectProcessor(getProject(), getEditor(), elements, "Inner");
    final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
    extractProcessor.setShowErrorDialogs(false);
    extractProcessor.prepare();
    extractProcessor.testPrepare();
    processor.setCreateInnerClass(createInnerClass);


    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ExtractMethodObjectHandler.run(getProject(), getEditor(), processor, extractProcessor);
      }
    });


    checkResultByFile("/refactoring/extractMethodObject/multipleExitPoints/" + testName + ".java" + ".after");
  }

  public void testStaticInner() throws Exception {
    doTest();
  }

  public void testInputOutput() throws Exception {
    doTest();
  }

  public void testOutputVarsReferences() throws Exception {
    doTest();
  }

  public void testMultilineDeclarations() throws Exception {
    doTest();
  }

  public void testConditionalExit() throws Exception {
    doTest();
  }

  public void testOutputVariable() throws Exception {
    doTest();
  }

  public void testUniqueObjectName() throws Exception {
    doTest();
  }

  public void testExtractedAssignmentExpression() throws Exception {
    doTest();
  }

  public void testExtractedIncExpression() throws Exception {
    doTest();
  }


  public void testWithInnerClasses() throws Exception {
    doTest();
  }

  public void testNonCanonicalNaming() throws Exception {
    doTest();
  }

  public void testExtractFromAnonymous() throws Exception {
    doTest();
  }

  public void testExtractFromIfStatementInsideAnonymous() throws Exception {
    doTest();
  }

  public void testConditionalExitWithoutCodeBlock() throws Exception {
    doTest();
  }

  public void testReturnExitStatement() throws Exception {
    doTest();
  }

  public void testFromStaticContext() throws Exception {
    doTest();
  }

  public void testBatchUpdateCausedByFormatter() throws Exception {
    doTest();
  }
}
