package com.intellij.codeInsight.daemon.quickFix;import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;

/**
 * @author ven
 */
public abstract class CreateLocalVarFromInstanceofTest extends LightIntentionActionTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createLocalVarFromInstanceof";
  }
}
