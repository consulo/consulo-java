package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.codeInspection.duplicateThrows.DuplicateThrowsInspection;
import com.intellij.testFramework.InspectionTestCase;

public abstract class DuplicateThrowsInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("duplicateThrows/" + getTestName(true), new DuplicateThrowsInspection());
  }

  public void testSimple() throws Exception{
    doTest();
  }
}
