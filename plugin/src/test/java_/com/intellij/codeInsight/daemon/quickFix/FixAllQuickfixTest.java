/*
 * User: anna
 * Date: 17-Jun-2007
 */
package com.intellij.codeInsight.daemon.quickFix;

import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;
import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.impl.codeInspection.dataFlow.DataFlowInspection;

public abstract class FixAllQuickfixTest extends LightQuickFixTestCase {
  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {
      new DataFlowInspection()
    };
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  @NonNls
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/fixAll";
  }
}