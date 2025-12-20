/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import consulo.language.editor.inspection.scheme.LocalInspectionToolWrapper;
import com.intellij.java.analysis.impl.codeInspection.redundantCast.RedundantCastInspection;
import consulo.content.bundle.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestCase;

public abstract class RedundantCast18Test extends InspectionTestCase {
  private void doTest() throws Exception {
    LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(new RedundantCastInspection());
    doTest("redundantCast/lambda/" + getTestName(false), tool, "java 1.5");
  }

  public void testLambdaContext() throws Exception { doTest(); }
  public void testMethodRefContext() throws Exception { doTest(); }
  public void testExpectedSupertype() throws Exception { doTest(); }

  @Override
  protected Sdk getTestProjectSdk() {
    Sdk sdk = IdeaTestUtil.getMockJdk17();
    //LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
    return sdk;
  }
}