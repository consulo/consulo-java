package com.intellij.codeInsight.daemon.quickFix;

import javax.annotation.Nonnull;

import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.analysis.impl.codeInspection.unusedImport.UnusedImportLocalInspection;


public abstract class EnableOptimizeImportsOnTheFlyTest extends LightQuickFixTestCase {
  @Nonnull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UnusedImportLocalInspection()};
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected void doAction(final String text, final boolean actionShouldBeAvailable, final String testFullPath, final String testName)
    throws Exception {
    boolean old = CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY;

    try {
      CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = false;
      IntentionAction action = findActionWithText(text);
      if (action == null && actionShouldBeAvailable) {
        fail("Action with text '" + text + "' is not available in test " + testFullPath);
      }
      if (action != null && actionShouldBeAvailable) {
        action.invoke(getProject(), getEditor(), getFile());
        assertTrue(CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY);
      }
    }
    finally {
      CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = old;
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/enableOptimizeImportsOnTheFly";
  }
}

