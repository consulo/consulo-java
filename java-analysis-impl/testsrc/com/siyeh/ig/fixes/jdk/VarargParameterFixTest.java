package com.siyeh.ig.fixes.jdk;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.jdk.VarargParameterInspection;
import com.siyeh.localize.InspectionGadgetsLocalize;

public class VarargParameterFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new VarargParameterInspection());
    myRelativePath = "jdk/vararg_parameter";
    myDefaultHint = InspectionGadgetsLocalize.variableArgumentMethodQuickfix().get();
  }

  public void testGenericType() { doTest(); }
}