package com.intellij.codeInsight.daemon;

import consulo.language.editor.FileHighlightingSetting;
import consulo.language.editor.internal.HighlightingSettingsPerFile;
import consulo.language.editor.inspection.LocalInspectionTool;
import com.intellij.java.analysis.impl.codeInspection.unusedImport.UnusedImportLocalInspection;
import consulo.language.psi.PsiFile;

public abstract class UnusedImportsTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/unusedImports";

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UnusedImportLocalInspection()};
  }

  public void test1() throws Exception { doTest(); }
  public void test2() throws Exception { doTest(); }

  public void testWithHighlightingOff() throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    PsiFile file = getFile();
    HighlightingSettingsPerFile settingsPerFile = HighlightingSettingsPerFile.getInstance(myProject);
    FileHighlightingSetting oldSetting = settingsPerFile.getHighlightingSettingForRoot(file);
    try {
      settingsPerFile.setHighlightingSettingForRoot(file, FileHighlightingSetting.NONE);
      doDoTest(true, false, false);
    }
    finally {
      settingsPerFile.setHighlightingSettingForRoot(file, oldSetting);
    }
  }

  public void testUnclosed() throws Exception { doTest(); }

  public void testQualified() throws Exception { doTest(); }

  public void testInnersOnDemand1() throws Exception { doTest(); }
  public void testInnersOnDemand2() throws Exception { doTest(); }
  public void testStaticImportingInner() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", BASE_PATH, true, false);
  }

  public void testImportFromSamePackage1() throws Exception {
    doTest(BASE_PATH+"/package1/a.java", BASE_PATH,true,false);
  }
  public void testImportFromSamePackage2() throws Exception {
    doTest(BASE_PATH+"/package1/b.java", BASE_PATH,true,false);
  }

  protected void doTest() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }
}