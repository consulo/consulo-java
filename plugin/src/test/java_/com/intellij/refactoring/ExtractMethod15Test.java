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

/*
 * User: anna
 * Date: 11-Jun-2009
 */
package com.intellij.refactoring;

import jakarta.annotation.Nonnull;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;

public abstract class ExtractMethod15Test extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/refactoring/extractMethod15/";

  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testCodeDuplicatesWithMultOccurences() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = ExtractMethodTest.performExtractMethod(true, true, getEditor(), getFile(), getProject());
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
}