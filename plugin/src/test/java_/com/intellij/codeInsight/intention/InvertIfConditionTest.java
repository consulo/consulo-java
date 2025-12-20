/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 22, 2002
 * Time: 2:58:42 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.java.language.JavaLanguage;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.codeStyle.CommonCodeStyleSettings;

public abstract class InvertIfConditionTest extends LightIntentionActionTestCase {

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  private static final String BASE_PATH = "/codeInsight/invertIfCondition/";
  private boolean myElseOnNewLine;

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }

  @Override
  protected void beforeActionStarted(String testName, String contents) {
    super.beforeActionStarted(testName, contents);
    CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    myElseOnNewLine = settings.ELSE_ON_NEW_LINE;
    settings.ELSE_ON_NEW_LINE = !contents.contains("else on the same line");
  }

  @Override
  protected void afterActionCompleted(String testName, String contents) {
    super.afterActionCompleted(testName, contents);
    CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = myElseOnNewLine;
  }

  public void test() throws Exception {
    doAllTests();
  }
}
