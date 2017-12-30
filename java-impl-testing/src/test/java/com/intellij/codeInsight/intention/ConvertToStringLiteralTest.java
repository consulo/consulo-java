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

package com.intellij.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import consulo.java.JavaQuickFixBundle;


public class ConvertToStringLiteralTest extends JavaCodeInsightFixtureTestCase {
  private String myIntention;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myIntention = JavaQuickFixBundle.message("convert.to.string.text");
  }

  public void testSimple() throws Exception {
    CodeInsightTestUtil.doIntentionTest(myFixture, myIntention, "Simple.java", "Simple_after.java");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/convertToStringLiteral/";
  }
}