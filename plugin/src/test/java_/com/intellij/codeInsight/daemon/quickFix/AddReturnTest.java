
package com.intellij.codeInsight.daemon.quickFix;

public abstract class AddReturnTest extends LightQuickFixTestCase {

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addReturn";
  }

}

