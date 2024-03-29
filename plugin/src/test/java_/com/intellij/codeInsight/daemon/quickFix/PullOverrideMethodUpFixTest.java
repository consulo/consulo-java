/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.quickFix;

import consulo.language.editor.intention.IntentionAction;

public abstract class PullOverrideMethodUpFixTest extends LightQuickFixTestCase {
  public void test1() throws Exception {
    doSingleTest("1.java");
  }

  public void test2() throws Exception {
    doSingleTest("2.java");
  }

  public void test3() throws Exception {
    doSingleTest("3.java");
  }

  public void test4() throws Exception {
    doSingleTest("4.java");
  }

  public void test6() throws Exception {
    doSingleTest("6.java");
  }

  public void testRefactoringIntentionsAvailable() throws Exception {
    doTestActionAvailable(5, "Pull members up");
    doTestActionAvailable(5, "Extract interface");
    doTestActionAvailable(5, "Extract superclass");
  }

  private void doTestActionAvailable(final int suffix, final String actionText) {
    final String testFullPath = getBasePath() + "/before" + suffix + ".java";
    configureByFile(testFullPath);
    doHighlighting();
    final IntentionAction action = findActionWithText(actionText);
    assertNotNull(action);
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/pullUp";
  }
}
