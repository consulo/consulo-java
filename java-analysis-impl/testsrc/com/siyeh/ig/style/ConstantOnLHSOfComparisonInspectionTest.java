package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class ConstantOnLHSOfComparisonInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/constant_on_lhs", new ConstantOnLHSOfComparisonInspection());
  }
}