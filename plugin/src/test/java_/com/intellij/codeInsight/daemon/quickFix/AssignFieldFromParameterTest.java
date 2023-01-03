package com.intellij.codeInsight.daemon.quickFix;

import org.jdom.Element;
import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CodeStyleSettingsManager;

/**
 * @author ven
 */
public abstract class AssignFieldFromParameterTest extends LightIntentionActionTestCase {
  private Element myOldSettings;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    myOldSettings = new Element("dummy2");
    settings.writeExternal(myOldSettings);
    settings.FIELD_NAME_PREFIX = "my";
    settings.STATIC_FIELD_NAME_PREFIX = "our";
  }

  @Override
  protected void tearDown() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.readExternal(myOldSettings);
    super.tearDown();
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/assignFieldFromParameter";
  }

}
