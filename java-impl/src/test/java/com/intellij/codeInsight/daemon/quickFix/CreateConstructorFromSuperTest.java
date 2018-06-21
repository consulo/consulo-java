package com.intellij.codeInsight.daemon.quickFix;

/**
 * @author ven
 */
public abstract class CreateConstructorFromSuperTest extends LightQuickFixTestCase {

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createConstructorFromSuper";
  }

}
