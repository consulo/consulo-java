package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.java.impl.codeInspection.defaultFileTemplateUsage.DefaultFileTemplateUsageInspection;
import consulo.content.bundle.Sdk;
import com.intellij.testFramework.InspectionTestCase;

public abstract class DefaultFileTemplateInspectionTest extends InspectionTestCase {
  @Override
  protected Sdk getTestProjectSdk() {
    final Sdk sdk = super.getTestProjectSdk();
    //LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    return sdk;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("defaultFileTemplateUsage/" + getTestName(true), new DefaultFileTemplateUsageInspection());
  }

  public void testDefaultFile() throws Exception{
    doTest();
  }
}
