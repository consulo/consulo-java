package com.intellij.codeInsight.daemon.quickFix;

public abstract class AddVariableInitializerTest extends LightQuickFixTestCase {

  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addVariableInitializer";
  }

}

