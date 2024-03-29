
package com.intellij.codeInsight.daemon.quickFix;

import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.analysis.impl.codeInspection.localCanBeFinal.LocalCanBeFinal;
import jakarta.annotation.Nonnull;

public abstract class SuppressLocalInspectionTest extends LightQuickFixTestCase {
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

