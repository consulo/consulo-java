package com.siyeh.ig.fixes.javadoc;

import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.javadoc.HtmlTagCanBeJavadocTagInspection;
import com.siyeh.localize.InspectionGadgetsLocalize;

public class HtmlTagCanBeJavadocTagFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new HtmlTagCanBeJavadocTagInspection());
    myRelativePath = "javadoc/html_tag_can_be_javadoc_tag";
    myDefaultHint = InspectionGadgetsLocalize.htmlTagCanBeJavadocTagQuickfix().get();
  }

  public void testBraces() { doTest(); }
  public void testSecond() { doTest(); }
  public void testMultiline() { doTest(); }
}