package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection;
import consulo.language.editor.inspection.scheme.LocalInspectionToolWrapper;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author cdr
 */
public abstract class RedundantArrayForVarargsCallInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("redundantArrayForVarargs/" + getTestName(true), new LocalInspectionToolWrapper(new RedundantArrayForVarargsCallInspection()),"java 1.5");
  }

  public void testIDEADEV15215() throws Exception { doTest(); }
  public void testIDEADEV25923() throws Exception { doTest(); }
  public void testNestedArray() throws Exception { doTest(); }
  public void testCheckEnumConstant() throws Exception { doTest(); }
  public void testGeneric() throws Exception { doTest(); }
}
