package com.siyeh.ig.fixes.style;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.StringBufferReplaceableByStringInspection;
import com.siyeh.localize.InspectionGadgetsLocalize;

public class StringBufferReplaceableWithStringFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new StringBufferReplaceableByStringInspection());
    myRelativePath = "style/replace_with_string";
    myDefaultHint = InspectionGadgetsLocalize.stringBufferReplaceableByStringQuickfix().get();
  }

  public void testSimpleStringBuffer() { doTest(); }
  public void testStringBuilderAppend() { doTest(InspectionGadgetsLocalize.stringBuilderReplaceableByStringQuickfix().get()); }
  public void testStringBufferVariable() { doTest(); }
  public void testStringBufferVariable2() { doTest(); }
  public void testStartsWithPrimitive() { doTest(); }
  public void testPrecedence() { doTest(InspectionGadgetsLocalize.stringBuilderReplaceableByStringQuickfix().get()); }
  public void testPrecedence2() { doTest(InspectionGadgetsLocalize.stringBuilderReplaceableByStringQuickfix().get()); }
  public void testPrecedence3() { doTest(InspectionGadgetsLocalize.stringBuilderReplaceableByStringQuickfix().get()); }
  public void testNonString1() { doTest(InspectionGadgetsLocalize.stringBuilderReplaceableByStringQuickfix().get()); }
  public void testNonString2() { doTest(InspectionGadgetsLocalize.stringBuilderReplaceableByStringQuickfix().get()); }
}
