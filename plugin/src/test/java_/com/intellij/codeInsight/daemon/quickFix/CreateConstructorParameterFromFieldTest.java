package com.intellij.codeInsight.daemon.quickFix;

import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import jakarta.annotation.Nonnull;

/**
 * @author cdr
 */
public abstract class CreateConstructorParameterFromFieldTest extends LightQuickFixTestCase {
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
