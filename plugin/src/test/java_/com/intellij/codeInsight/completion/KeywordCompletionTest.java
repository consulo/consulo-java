/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import jakarta.annotation.Nonnull;

import com.intellij.JavaTestUtil;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.NonNls;

/**
 * @author ik
 * @since 20.02.2003
 */
public abstract class KeywordCompletionTest extends LightCompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/keywords";

  private static final String[] FILE_SCOPE_KEYWORDS = {
    "package", "public", "private", "import", "final", "class", "interface", "abstract", "enum", "default", null};
  private static final String[] CLASS_SCOPE_KEYWORDS = {
    "public", "private", "protected", "import", "final", "class", "interface", "abstract", "enum", "default", null};
  private static final String[] CLASS_SCOPE_KEYWORDS_2 = {
    "package", "public", "private", "protected", "transient", "volatile", "static", "import", "final", "class", "interface", "abstract", "default"};
  private static final String[] INTERFACE_SCOPE_KEYWORDS = {
    "package", "public", "private", "protected", "transient", "volatile", "static", "import", "final", "class", "interface", "abstract", "default"};

  @Nonnull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testFileScope1() throws Exception { doTest(8, FILE_SCOPE_KEYWORDS); }
  public void testFileScope2() throws Exception { doTest(7, CLASS_SCOPE_KEYWORDS); }
  public void testClassScope1() throws Exception { doTest(5, CLASS_SCOPE_KEYWORDS); }
  public void testClassScope2() throws Exception { doTest(4, CLASS_SCOPE_KEYWORDS); }
  public void testClassScope3() throws Exception { doTest(0, CLASS_SCOPE_KEYWORDS); }
  public void testClassScope4() throws Exception { doTest(10, CLASS_SCOPE_KEYWORDS_2); }
  public void testInterfaceScope() throws Exception { doTest(8, INTERFACE_SCOPE_KEYWORDS); }
  public void testAfterAnnotations() throws Exception { doTest(6, "public", "final", "class", "interface", "abstract", "enum", null); }
  public void testExtends1() throws Exception { doTest(2, "extends", "implements", null); }
  public void testExtends2() throws Exception { doTest(1, "extends", "implements", "AAA", "BBB", "instanceof"); }
  public void testExtends3() throws Exception { doTest(2, "extends", "implements", "AAA", "BBB", "CCC", "instanceof"); }
  public void testExtends4() throws Exception { doTest(2, "extends", "implements", "AAA", "BBB", "CCC", "instanceof"); }
  public void testExtends5() throws Exception { doTest(1, "extends", "implements", "AAA", "BBB", "CCC", "instanceof"); }
  public void testExtends6() throws Exception { doTest(1, "extends", "implements", "AAA", "BBB", "CCC", "instanceof"); }
  public void testExtends7() throws Exception { doTest(false); }
  public void testExtends8() throws Exception { doTest(false); }
  public void testExtends9() throws Exception { doTest(false); }
  public void testExtends10() throws Exception { doTest(false); }
  public void testExtends11() throws Exception { doTest(false); }
  public void testExtends12() throws Exception { doTest(false); }
  public void testExtends13() throws Exception { doTest(false); }
  public void testSynchronized1() throws Exception { doTest(false); }

  public void testSynchronized2() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).SPACE_BEFORE_SYNCHRONIZED_PARENTHESES = false;
    doTest(false);
  }

  public void testMethodScope1() throws Exception { doTest(1, "throws"); }
  public void testMethodScope2() throws Exception { doTest(1, "final", "public", "static", "volatile", "abstract"); }
  public void testMethodScope3() throws Exception { doTest(1, "final", "public", "static", "volatile", "abstract", "throws", "instanceof"); }
  public void testMethodScope4() throws Exception { doTest(6, "final", "try", "for", "while", "return", "throw"); }
  public void testMethodScope5() throws Exception { doTest(true); }
  public void testExtraBracketAfterFinally1() throws Exception { doTest(false); }
  public void testExtraBracketAfterFinally2() throws Exception { doTest(false); }
  public void testExtendsInCastTypeParameters() throws Exception { doTest(false); }
  public void testExtendsInCastTypeParameters2() throws Exception { doTest(2, "extends", "super"); }
  public void testExtendsWithRightContextInClassTypeParameters() throws Exception { doTest(false); }
  public void testTrueInVariableDeclaration() throws Exception { doTest(true); }
  public void testNullInIf() throws Exception { doTest(true); }
  public void testNullInReturn() throws Exception { doTest(true); }
  public void testExtendsInMethodParameters() throws Exception { doTest(false); }
  public void testInstanceOf1() throws Exception { doTest(false); }
  public void testInstanceOf2() throws Exception { doTest(false); }
  public void testInstanceOf3() throws Exception { doTest(false); }
  public void testCatchFinally() throws Exception { doTest(2, "catch", "finally"); }
  public void testSuper1() throws Exception { doTest(1, "super"); }
  public void testSuper2() throws Exception { doTest(0, "super"); }
  public void testContinue() throws Exception { doTest(false); }
  public void testThrowsOnSeparateLine() throws Exception { doTest(true); }
  public void testDefaultInAnno() throws Exception { doTest(false); }
  public void testNullInMethodCall() throws Exception { doTest(true); }
  public void testNullInMethodCall2() throws Exception { doTest(false); }
  public void testNewInMethodRefs() throws Exception { doTest(1, "new"); }
  public void testSpaceAfterInstanceof() throws Exception { doTest(false); }
  public void testInstanceofAfterUnresolved() throws Exception { doTest(1, "instanceof"); }
  public void testAbstractInInterface() throws Exception { doTest(1, "abstract"); }
  public void testCharInAnnotatedParameter() throws Exception { doTest(1, "char"); }
  public void testReturnInTernary() throws Exception { doTest(1, "return"); }
  public void testFinalAfterParameterAnno() throws Exception { doTest(2, "final", "float", "class"); }
  public void testFinalAfterParameterAnno2() throws Exception { doTest(2, "final", "float", "class"); }
  public void testFinalAfterCase() throws Exception { doTest(3, "final", "float", "class"); }
  public void testFinalInTryWithResources() throws Exception { doTest(1, "final", "float", "class"); }
  public void testNoFinalAfterTryBody() throws Exception { doTest(1, "final", "finally"); }
  public void testClassInMethod() throws Exception { doTest(2, "class", "char"); }
  public void testIntInClassArray() throws Throwable { doTest(2, "int", "char", "final"); }
  public void testIntInClassArray2() throws Throwable { doTest(2, "int", "char", "final"); }
  public void testIntInClassArray3() throws Throwable { doTest(2, "int", "char", "final"); }
  public void testArrayClass() throws Throwable { doTest(1, "class", "interface"); }
  public void testIntInGenerics() throws Throwable { doTest(2, "int", "char", "final"); }
  public void testIntInGenerics2() throws Throwable { doTest(2, "int", "char", "final"); }

  public void testTryInExpression() throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(true) + ".java");
    assertEquals("toString", myItems[0].getLookupString());
    assertEquals("this", myItems[1].getLookupString());
  }

  private void doTest(boolean select) throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(true) + ".java");
    if (select) {
      selectItem(myItems[0]);
    }
    checkResultByFile(BASE_PATH + "/" + getTestName(true) + "_after.java");
  }

  // todo: check included/excluded variants separately
  protected void doTest(int finalCount, @NonNls String... values) {
    configureByFile(BASE_PATH + "/" + getTestName(true) + ".java");
    testByCount(finalCount, values);
  }
}