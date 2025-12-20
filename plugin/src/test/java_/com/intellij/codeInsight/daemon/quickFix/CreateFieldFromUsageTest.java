package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.java.language.JavaLanguage;
import consulo.application.Result;
import consulo.language.editor.WriteCommandAction;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.codeStyle.CommonCodeStyleSettings;

/**
 * @author ven
 */
public abstract class CreateFieldFromUsageTest extends LightQuickFixTestCase{

  public void testAnonymousClass() throws Exception { doSingleTest(); }
  public void testExpectedTypes() throws Exception { doSingleTest(); }
  public void testInterface() throws Exception { doSingleTest(); }
  public void testMultipleTypes() throws Exception { doSingleTest(); }
  public void testMultipleTypes2() throws Exception { doSingleTest(); }
  public void testParametericMethod() throws Exception { doSingleTest(); }
  public void testQualifyInner() throws Exception { doSingleTest(); }
  public void testTypeArgsFormatted() throws Exception { doSingleTest(); }
  public void testInsideStaticInnerClass() throws Exception { doSingleTest(); }
  public void testCreateFromEquals() throws Exception { doSingleTest(); }
  public void testCreateFromEqualsToPrimitiveType() throws Exception { doSingleTest(); }
  public void testWithAlignment() throws Exception {
    CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean old = settings.ALIGN_GROUP_FIELD_DECLARATIONS;
    try {
      settings.ALIGN_GROUP_FIELD_DECLARATIONS = true;
      doSingleTest();
    }
    finally {
      settings.ALIGN_GROUP_FIELD_DECLARATIONS = old;
    }
  }

  public void testSortByRelevance() throws Exception {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(Result result) throws Exception {
        VirtualFile foo = getSourceRoot().createChildDirectory(this, "foo").createChildData(this, "Foo.java");
        VfsUtil.saveText(foo, "package foo; public class Foo { public void put(Object key, Object value) {} }");
      }
    }.execute();

    doSingleTest();
  }

  public void testDependantConstant() throws Exception {
    doSingleTest();
  }

  public void testArrayBraces() throws Exception {
    doSingleTest();
  }

  protected void doSingleTest() {
    doSingleTest(getTestName(false) + ".java");
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createFieldFromUsage";
  }

}
