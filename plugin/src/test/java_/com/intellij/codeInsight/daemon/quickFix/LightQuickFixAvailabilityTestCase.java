package com.intellij.codeInsight.daemon.quickFix;

import consulo.language.editor.intention.IntentionAction;

/**
 * tests corresponding intention for availability only, does not invoke action
 * @author cdr
 */
public abstract class LightQuickFixAvailabilityTestCase extends LightQuickFixTestCase {
  @Override
  protected void doAction(String text, boolean actionShouldBeAvailable, String testFullPath, String testName)
    throws Exception {
    IntentionAction action = findActionWithText(text);
    assertTrue("Action with text '" + text + "' is " + (action == null ? "not " :"") +
               "available in test " + testFullPath,
      (action != null) == actionShouldBeAvailable);
  }
}
