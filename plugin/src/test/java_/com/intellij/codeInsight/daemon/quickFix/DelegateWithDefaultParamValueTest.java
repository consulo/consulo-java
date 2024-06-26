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

import consulo.ide.impl.idea.codeInsight.template.impl.TemplateManagerImpl;
import consulo.language.editor.template.TemplateState;

/**
 * @author anna
 */
public abstract class DelegateWithDefaultParamValueTest extends LightQuickFixTestCase {
  @Override
  protected void doAction(String text, boolean actionShouldBeAvailable, String testFullPath, String testName)
    throws Exception {
    TemplateManagerImpl.setTemplateTesting(getProject(), getTestRootDisposable());
    super.doAction(text, actionShouldBeAvailable, testFullPath, testName);

    if (actionShouldBeAvailable) {
      TemplateState state = TemplateManagerImpl.getTemplateState(getEditor());
      assert state != null;
      state.gotoEnd(false);
    }
  }

  public void test() throws Exception {
    doAllTests();

  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/delegateWithDefaultValue";
  }
}
