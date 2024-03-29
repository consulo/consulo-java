package com.siyeh.ig.threading;

import consulo.content.bundle.Sdk;
import com.intellij.java.language.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.IGInspectionTestCase;

public class SynchronizationOnLocalVariableOrMethodParameterInspectionTest extends IGInspectionTestCase {

  @Override
  protected Sdk getTestProjectSdk() {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
    return IdeaTestUtil.getMockJdk17();
  }

  public void test() throws Exception {
    doTest("com/siyeh/igtest/threading/synchronization_on_local_variable_or_method_parameter",
           new SynchronizationOnLocalVariableOrMethodParameterInspection());
  }
}
