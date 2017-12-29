
package com.intellij.codeInsight.daemon.quickFix;

public class CastMethodParametersTest extends LightQuickFixTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
   // LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_3);
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/castMethodParameters";
  }
}

