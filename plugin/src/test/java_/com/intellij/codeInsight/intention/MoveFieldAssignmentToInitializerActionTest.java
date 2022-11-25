package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;

/**
 * @author ven
 */
public abstract class MoveFieldAssignmentToInitializerActionTest extends LightIntentionActionTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/moveFieldAssignmentToInitializer";
  }
}
