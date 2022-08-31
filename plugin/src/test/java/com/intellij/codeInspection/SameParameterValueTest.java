package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.testFramework.InspectionTestCase;

public abstract class SameParameterValueTest extends InspectionTestCase {
  private final SameParameterValueInspection myTool = new SameParameterValueInspection();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private String getTestDir() {
    return "sameParameterValue/" + getTestName(true);
  }

  public void testEntryPoint() throws Exception {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testWithoutDeadCode() throws Exception {
    doTest(getTestDir(), myTool, false, false);
  }

  public void testVarargs() throws Exception {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testSimpleVararg() throws Exception {
    doTest(getTestDir(), myTool, false, true);
  }
}
