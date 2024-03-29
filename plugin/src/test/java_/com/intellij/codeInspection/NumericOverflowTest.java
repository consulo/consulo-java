package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.java.analysis.impl.codeInspection.NumericOverflowInspection;
import com.intellij.testFramework.InspectionTestCase;

public abstract class NumericOverflowTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("numericOverflow/" + getTestName(true), new NumericOverflowInspection());
  }


  public void testSimple() throws Exception {
    doTest();
  }
}
