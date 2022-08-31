
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.java.language.LanguageLevel;



public abstract class ChangeParameterClassTest extends LightQuickFix15TestCase {

  public void test() throws Exception {
    setLanguageLevel(LanguageLevel.JDK_1_5);
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/changeParameterClass";
  }

}

