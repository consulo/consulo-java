package com.intellij.codeInsight.daemon.quickFix;

public abstract class RemoveQualifierTest extends LightQuickFixTestCase {

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/removeQualifier";
  }
}
