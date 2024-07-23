package com.siyeh.ig.fixes.jdk;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.jdk.ForeachStatementInspection;
import com.siyeh.localize.InspectionGadgetsLocalize;

public class ForeachStatementFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ForeachStatementInspection());
    myRelativePath = "jdk/foreach_statement";
    myDefaultHint = InspectionGadgetsLocalize.extendedForStatementReplaceQuickfix().get();
  }

  public void testBareCollectionLoop() { doTest(); }
  public void testBoundedTypes() { doTest(); }
  public void testGenericTypes() { doTest(); }
  public void testPrecedence() { doTest(); }
  public void testWildcards() { doTest(); }
}