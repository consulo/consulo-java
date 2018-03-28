package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import javax.annotation.Nonnull;

/**
 * @author cdr
 */
public class CreateConstructorParameterFromFieldTest extends LightQuickFixTestCase {
  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{ new UnusedSymbolLocalInspection()};
  }


  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createConstructorParameterFromField";
  }
}
