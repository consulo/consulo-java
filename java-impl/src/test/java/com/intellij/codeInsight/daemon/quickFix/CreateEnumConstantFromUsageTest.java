package com.intellij.codeInsight.daemon.quickFix;

/**
 * @author ven
 */
public abstract class CreateEnumConstantFromUsageTest extends LightQuickFix15TestCase{

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createEnumConstantFromUsage";
  }

}
