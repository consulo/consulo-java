package com.intellij.codeInsight.daemon.quickFix;

public abstract class ChangeStringLiteralToCharInMethodCallTest extends LightQuickFixTestCase {

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/stringToCharacterLiteral";
  }
}
