package com.intellij.codeInsight.daemon.quickFix;

public abstract class AddNewArrayExpressionTest extends LightQuickFixTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addNewArrayExpression";
  }
}

