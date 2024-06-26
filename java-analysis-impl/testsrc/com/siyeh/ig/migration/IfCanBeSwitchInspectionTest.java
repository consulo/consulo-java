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
package com.siyeh.ig.migration;

import consulo.language.editor.inspection.scheme.LocalInspectionToolWrapper;
import consulo.content.bundle.Sdk;
import com.intellij.java.language.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.IGInspectionTestCase;

public class IfCanBeSwitchInspectionTest extends IGInspectionTestCase {

  @Override
  protected Sdk getTestProjectSdk() {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_7);
    return IdeaTestUtil.getMockJdk17();
  }

  public void test() throws Exception {
    final IfCanBeSwitchInspection inspection = new IfCanBeSwitchInspection();
    inspection.suggestIntSwitches = true;
      doTest("com/siyeh/igtest/migration/if_switch", new LocalInspectionToolWrapper(inspection));
  }
}
