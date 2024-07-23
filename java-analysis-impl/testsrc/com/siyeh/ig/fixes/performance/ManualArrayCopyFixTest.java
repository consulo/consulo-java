package com.siyeh.ig.fixes.performance;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.ManualArrayCopyInspection;
import com.siyeh.localize.InspectionGadgetsLocalize;

public class ManualArrayCopyFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ManualArrayCopyInspection());
    myRelativePath = "performance/replace_with_system_arraycopy";
    myDefaultHint = InspectionGadgetsLocalize.manualArrayCopyReplaceQuickfix().get();
  }

  public void testSimple() { doTest(); }
  public void testDecrement() { doTest(); }
}