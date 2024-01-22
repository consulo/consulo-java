package com.intellij.codeInsight.daemon.quickFix;

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.impl.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;


public abstract class GenerifyFileTest extends LightQuickFixAvailabilityTestCase {

  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new UncheckedWarningLocalInspection()};
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/generifyFile";
  }
}

