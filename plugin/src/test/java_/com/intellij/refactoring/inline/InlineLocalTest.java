package com.intellij.refactoring.inline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import javax.annotation.Nonnull;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.refactoring.inline.InlineLocalHandler;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.java.language.LanguageLevel;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiLocalVariable;
import consulo.language.psi.PsiReference;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.language.editor.refactoring.RefactoringBundle;
import com.intellij.testFramework.LightCodeInsightTestCase;
import consulo.util.collection.ContainerUtil;
import consulo.language.editor.TargetElementUtil;
import consulo.codeInsight.TargetElementUtilEx;

/**
 * @author ven
 */
public abstract class InlineLocalTest extends LightCodeInsightTestCase {
  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInference () throws Exception {
    doTest(false);
  }

  public void testQualifier () throws Exception {
    doTest(false);
  }

  public void testInnerInnerClass() throws Exception {
    doTest(true);
  }

  public void testIDEADEV950 () throws Exception {
    doTest(false);
  }

  public void testNoRedundantCasts () throws Exception {
    doTest(false);
  }

  public void testIdeaDEV9404 () throws Exception {
    doTest(false);
  }

  public void testIDEADEV12244 () throws Exception {
    doTest(false);
  }

  public void testIDEADEV10376 () throws Exception {
    doTest(true);
  }

  public void testIDEADEV13151 () throws Exception {
    doTest(true);
  }

  public void testArrayInitializer() throws Exception {
    doTest(false);
  }

  public void testNonWriteUnaryExpression() throws Exception {
    doTest(true);
  }

  public void testNewExpression() throws Exception {
    doTest(false);
  }

  public void testNewExpressionWithDiamond() throws Exception {
    doTest(false);
  }

  public void testNewExpressionWithPreservedDiamond() throws Exception {
    doTest(false);
  }

  public void testAugmentedAssignment() throws Exception {
    String exception = null;
    try {
      doTest(false);
    }
    catch(RuntimeException ex) {
      exception = ex.getMessage();
    }
    String error = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing", "text"));
    assertEquals(error, exception);
  }

  public void testUsedInInnerClass() throws Exception {       // IDEADEV-28786
    doTest(true);
  }

  public void testUsedInInnerClass2() throws Exception {       // IDEADEV-28786
    doTest(true);
  }

  public void testUsedInInnerClass3() throws Exception {       // IDEADEV-28786
    doTest(true);
  }

  public void testUsedInInnerClass4() throws Exception {       // IDEADEV-28786
    doTest(true);
  }

  public void testAnotherDefinitionUsed() throws Exception {
    doTest(true, "Cannot perform refactoring.\nAnother variable 'bar' definition is used together with inlined one.");
  }

  public void testAnotherDefinitionUsed1() throws Exception {
    doTest(false, "Cannot perform refactoring.\nAnother variable 'bar' definition is used together with inlined one.");
  }

  public void testTypeArgumentsStatic() throws Exception {
    doTest(true);
  }

  public void testTypeArguments() throws Exception {
    doTest(true);
  }

  public void testWildcard() throws Exception {
    doTest(true);
  }

  public void testStaticImported() throws Exception {
    doTest(true);
  }

  public void testQualified() throws Exception {
    doTest(true);
  }

  public void testAssignmentToArrayElement() throws Exception {
    doTest(true, "Cannot perform refactoring.\n" +
                 "Variable 'arr' is accessed for writing.");
  }

  public void testArrayIndex() throws Exception {
    doTest(true);
  }

  public void testNonEqAssignment() throws Exception {
    doTest(false, "Cannot perform refactoring.\n" +
                  "Variable 'x' is accessed for writing.");
  }

  public void testInlineFromTryCatch() throws Exception {
    doTest(true, "Unable to inline outside try/catch statement");
  }
  
  public void testInlineFromTryCatchAvailable() throws Exception {
    doTest(true);
  }
  
  public void testConditionExpr() throws Exception {
    doTest(true);
  }
  
  public void testLambdaExpr() throws Exception {
    doTest(true);
  }
  
  public void testLambdaExprAsRefQualifier() throws Exception {
    doTest(true);
  }

  public void testMethodRefAsRefQualifier() throws Exception {
    doTest(true);
  }

  public void testLocalVarInsideLambdaBody() throws Exception {
    doTest(true);
  }

  public void testLocalVarInsideLambdaBody1() throws Exception {
    doTest(true);
  }
  
  public void testLocalVarInsideLambdaBody2() throws Exception {
    doTest(true);
  }

  public void testCastAroundLambda() throws Exception {
    doTest(true);
  }

  public void testNoCastAroundLambda() throws Exception {
    doTest(true);
  }

  public void testUncheckedCast() throws Exception {
    doTest(true);
  }

  public void testUncheckedCastNotNeeded() throws Exception {
    doTest(true);
  }

  public void testCastNotNeeded() throws Exception {
    doTest(true);
  }

  public void testEnclosingThisExpression() throws Exception {
    doTest(true);
  }

  public void testParentStaticQualifier() throws Exception {
    doTest(true);
  }

  public void testLocalVarInsideLambdaBodyWriteUsage() throws Exception {
    doTest(true, "Cannot perform refactoring.\n" +
                 "Variable 'hello' is accessed for writing.");
  }

  private void doTest(final boolean inlineDef, String conflictMessage) throws Exception {
    try {
      doTest(inlineDef);
      fail("Conflict was not detected");
    }
    catch (RuntimeException e) {
      assertEquals(e.getMessage(), conflictMessage);
    }
  }


  private void doTest(final boolean inlineDef) throws Exception {
    setLanguageLevel(LanguageLevel.JDK_1_7);
    String name = getTestName(false);
    String fileName = "/refactoring/inlineLocal/" + name + ".java";
    configureByFile(fileName);
    if (!inlineDef) {
      performInline(getProject(), myEditor);
    }
    else {
      performDefInline(getProject(), myEditor);
    }
    checkResultByFile(fileName + ".after");
  }

  public static void performInline(Project project, Editor editor) {
    PsiElement element = TargetElementUtil
      .findTargetElement(editor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED, TargetElementUtilEx.REFERENCED_ELEMENT_ACCEPTED));
    assertTrue(element instanceof PsiLocalVariable);

    InlineLocalHandler.invoke(project, editor, (PsiLocalVariable)element, null);
  }

  public static void performDefInline(Project project, Editor editor) {
    PsiReference reference = TargetElementUtil.findReference(editor);
    assertTrue(reference instanceof PsiReferenceExpression);
    final PsiElement local = reference.resolve();
    assertTrue(local instanceof PsiLocalVariable);

    InlineLocalHandler.invoke(project, editor, (PsiLocalVariable)local, (PsiReferenceExpression)reference);
  }
}
