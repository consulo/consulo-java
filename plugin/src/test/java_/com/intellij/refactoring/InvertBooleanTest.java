package com.intellij.refactoring;

import static org.junit.Assert.assertTrue;

import jakarta.annotation.Nonnull;
import com.intellij.JavaTestUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import com.intellij.java.impl.refactoring.invertBoolean.InvertBooleanProcessor;
import consulo.util.collection.ContainerUtil;
import consulo.language.editor.TargetElementUtil;
import consulo.codeInsight.TargetElementUtilEx;

/**
 * @author ven
 */
public abstract class InvertBooleanTest extends LightRefactoringTestCase {
  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private static final String TEST_ROOT = "/refactoring/invertBoolean/";

  public void test1() throws Exception { doTest(); }

  public void test2() throws Exception { doTest(); } //inverting breaks overriding

  public void testParameter() throws Exception { doTest(); } //inverting boolean parameter

  public void testParameter1() throws Exception { doTest(); } //inverting boolean parameter more advanced stuff
  public void testUnusedReturnValue() throws Exception { doTest(); }

  public void testInnerClasses() throws Exception {doTest();}
  public void testAnonymousClasses() throws Exception {doTest();}

  private void doTest() throws Exception {
    configureByFile(TEST_ROOT + getTestName(true) + ".java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, ContainerUtil.newHashSet(TargetElementUtilEx.ELEMENT_NAME_ACCEPTED));
    assertTrue(element instanceof PsiNamedElement);

    PsiNamedElement namedElement = (PsiNamedElement)element;
    String name = namedElement.getName();
    new InvertBooleanProcessor(namedElement, name + "Inverted").run();
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }

}
