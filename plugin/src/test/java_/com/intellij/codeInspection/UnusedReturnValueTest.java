/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 07-Aug-2006
 * Time: 20:34:37
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.testFramework.InspectionTestCase;

public abstract class UnusedReturnValueTest extends InspectionTestCase {
  private final UnusedReturnValue myTool = new UnusedReturnValue();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("unusedReturnValue/" + getTestName(true), myTool);
  }


  public void testNonLiteral() throws Exception {
    doTest();
  }

  public void testNative() throws Exception {
    doTest();
  }

  public void testHierarchy() throws Exception {
    doTest();
  }

  
  public void testSimpleSetter() throws Exception {
    try {
      myTool.IGNORE_BUILDER_PATTERN = true;
      doTest();
    }
    finally {
      myTool.IGNORE_BUILDER_PATTERN = false;
    }
  }
}