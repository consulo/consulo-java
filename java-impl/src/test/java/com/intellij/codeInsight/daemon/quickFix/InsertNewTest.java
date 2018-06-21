package com.intellij.codeInsight.daemon.quickFix;

public abstract class InsertNewTest extends LightQuickFixTestCase {

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/insertNew";
  }

}

