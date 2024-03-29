
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.java.impl.codeInspection.unneededThrows.RedundantThrowsDeclaration;


public abstract class MethodThrowsTest extends LightQuickFixTestCase {

  public void test() throws Exception { enableInspectionTool(new RedundantThrowsDeclaration()); doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/methodThrows";
  }

}

