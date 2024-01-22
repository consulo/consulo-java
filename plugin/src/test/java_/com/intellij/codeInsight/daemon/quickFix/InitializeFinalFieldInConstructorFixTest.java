package com.intellij.codeInsight.daemon.quickFix;

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.impl.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;

public abstract class InitializeFinalFieldInConstructorFixTest extends LightQuickFixTestCase {
  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{ new UnusedSymbolLocalInspection()};
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/initializeFinalFieldInConstructor";
  }
}