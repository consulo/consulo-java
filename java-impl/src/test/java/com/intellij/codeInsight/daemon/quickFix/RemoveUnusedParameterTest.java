
package com.intellij.codeInsight.daemon.quickFix;

import javax.annotation.Nonnull;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;


public class RemoveUnusedParameterTest extends LightQuickFixTestCase {


  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{ new UnusedSymbolLocalInspection()};
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/removeUnusedParameter";
  }

}

