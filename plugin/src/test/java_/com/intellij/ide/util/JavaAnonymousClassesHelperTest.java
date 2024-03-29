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
package com.intellij.ide.util;

import com.intellij.JavaTestUtil;
import com.intellij.java.language.util.JavaAnonymousClassesHelper;
import com.intellij.java.language.psi.PsiAnonymousClass;
import consulo.language.psi.PsiElement;
import consulo.language.editor.util.PsiUtilBase;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JavaAnonymousClassesHelperTest extends LightCodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.configureByFile(getTestName(false) + ".java");
  }

  public void testSimple()                                    throws Exception {doTest(7);}
  public void testSimpleInConstructor()                       throws Exception {doTest(2);}
  public void testInsideAnonymousMethod()                     throws Exception {doTest(1);}
  public void testAnonymousParameterInAnonymousConstructor()  throws Exception {doTest(1);}
  public void testAnonymousParameterInAnonymousConstructor2() throws Exception {doTest(2);}

  @SuppressWarnings("ConstantConditions")
  private void doTest(int num) {
    final PsiElement element = PsiUtilBase.getElementAtCaret(myFixture.getEditor()).getParent().getParent();

    assert element instanceof PsiAnonymousClass : "There should be anonymous class at caret but " + element + " found";

    assertEquals("$" + num, JavaAnonymousClassesHelper.getName((PsiAnonymousClass)element));
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/anonymous/";
  }
}
