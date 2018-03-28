package com.intellij.codeInsight.daemon.quickFix;

import javax.annotation.Nonnull;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;


public class GenerifyFileTest extends LightQuickFixAvailabilityTestCase {

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

