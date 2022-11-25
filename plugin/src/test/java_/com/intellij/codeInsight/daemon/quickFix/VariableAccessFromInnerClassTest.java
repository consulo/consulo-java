
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.java.language.LanguageLevel;

public abstract class VariableAccessFromInnerClassTest extends VariableAccessFromInnerClass18Test {

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/mustBeFinal";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_7;
  }
}

