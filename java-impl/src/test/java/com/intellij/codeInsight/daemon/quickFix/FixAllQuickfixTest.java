/*
 * User: anna
 * Date: 17-Jun-2007
 */
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

public class FixAllQuickfixTest extends LightQuickFixTestCase {
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