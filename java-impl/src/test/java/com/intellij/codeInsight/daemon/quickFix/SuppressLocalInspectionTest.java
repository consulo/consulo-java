
package com.intellij.codeInsight.daemon.quickFix;

import javax.annotation.Nonnull;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;

public class SuppressLocalInspectionTest extends LightQuickFixTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
   // LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_3);
  }

  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new LocalCanBeFinal()};
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/suppressLocalInspection";
  }

}

