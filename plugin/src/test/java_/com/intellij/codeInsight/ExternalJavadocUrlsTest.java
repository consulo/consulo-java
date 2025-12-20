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
package com.intellij.codeInsight;

import static org.junit.Assert.*;

import com.intellij.java.impl.lang.java.JavaDocumentationProvider;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * User: anna
 * Date: 11/27/11
 */
public abstract class ExternalJavadocUrlsTest extends LightCodeInsightFixtureTestCase {
  private void doTest(String text, String... expectedSignature) {
    myFixture.configureByText("Test.java", text);
    PsiElement elementAtCaret = myFixture.getElementAtCaret();
    PsiMethod member = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod.class, false);
    assertNotNull(member);
    String signature = JavaDocumentationProvider.formatMethodSignature(member, true, false);
    assertNotNull(signature);
    assertEquals("found:" + signature, expectedSignature[0], signature);
  }

  public void testVarargs() {
    doTest("class Test {\n" +
           " void fo<caret>o(Class<?>... cl){}\n" +
           "}", "foo(java.lang.Class...)");

  }
  
  public void testTypeParams() {
    doTest("class Test {\n" +
           " <T> void so<caret>rt(T[] a, Comparator<? super T> c) {}\n" +
           "}\n" +
           " class Comparator<X>{}", "sort(T[], Comparator)");
  }
}
