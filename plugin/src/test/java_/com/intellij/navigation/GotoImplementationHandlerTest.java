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

package com.intellij.navigation;


import consulo.ide.impl.idea.codeInsight.navigation.GotoImplementationHandler;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public abstract class GotoImplementationHandlerTest extends JavaCodeInsightFixtureTestCase {

  public void testMultipleImplsFromAbstractCall() throws Throwable {
    PsiFile file = myFixture.addFileToProject("Foo.java", "public abstract class Hello {\n" +
                                                          "    abstract void foo();\n" +
                                                          "\n" +
                                                          "    class A {\n" +
                                                          "        {\n" +
                                                          "            fo<caret>o();\n" +
                                                          "        }\n" +
                                                          "    }\n" +
                                                          "    class Hello1 extends Hello {\n" +
                                                          "        void foo() {}\n" +
                                                          "    }\n" +
                                                          "    class Hello2 extends Hello {\n" +
                                                          "        void foo() {}\n" +
                                                          "    }\n" +
                                                          "}\n" +
                                                          "\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = new GotoImplementationHandler().getSourceAndTargetElements(myFixture.getEditor(), file).targets;
    assertEquals(2, impls.length);
  }

  public void testShowSelfNonAbstract() throws Throwable {
    //fails if groovy plugin is enabled: org.jetbrains.plugins.groovy.codeInsight.JavaClsMethodElementEvaluator
    PsiFile file = myFixture.addFileToProject("Foo.java", "public class Hello {\n" +
                                                          "    void foo(){}\n" +
                                                          "\n" +
                                                          "    class A {\n" +
                                                          "        {\n" +
                                                          "            fo<caret>o();\n" +
                                                          "        }\n" +
                                                          "    }\n" +
                                                          "    class Hello1 extends Hello {\n" +
                                                          "        void foo() {}\n" +
                                                          "    }\n" +
                                                          "    class Hello2 extends Hello {\n" +
                                                          "        void foo() {}\n" +
                                                          "    }\n" +
                                                          "}\n" +
                                                          "\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = new GotoImplementationHandler().getSourceAndTargetElements(myFixture.getEditor(), file).targets;
    assertEquals(3, impls.length);
  }

  public void testMultipleImplsFromStaticCall() throws Throwable {
    PsiFile file = myFixture.addFileToProject("Foo.java", "public abstract class Hello {\n" +
                                                          "    static void bar (){}\n" +
                                                          "    class Hello1 extends Hello {\n" +
                                                          "    }\n" +
                                                          "    class Hello2 extends Hello {\n" +
                                                          "    }\n" +
                                                          "class D {\n" +
                                                          "    {\n" +
                                                          "        He<caret>llo.bar();\n" +
                                                          "    }\n" +
                                                          "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = new GotoImplementationHandler().getSourceAndTargetElements(myFixture.getEditor(), file).targets;
    assertEquals(2, impls.length);
  }

  public void testFilterOutImpossibleVariants() throws Throwable {
    PsiFile file = myFixture.addFileToProject("Foo.java", "interface A {\n" +
                                                          "    void save();\n" +
                                                          "}\n" +
                                                          "interface B extends A {\n" +
                                                          "    void foo();\n" +
                                                          "}\n" +
                                                          "class X implements B {\n" +
                                                          "    public void foo() { }\n" +
                                                          "    public void save(){}\n" +
                                                          "}\n" +
                                                          "class Y implements A {\n" +
                                                          "    public void save(){}\n" +
                                                          "}\n" +
                                                          "class App {\n" +
                                                          "    private B b;\n" +
                                                          "    private void some() {\n" +
                                                          "        b.sa<caret>ve();\n" +
                                                          "    }\n" +
                                                          "}");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());

    final PsiElement[] impls = new GotoImplementationHandler().getSourceAndTargetElements(myFixture.getEditor(), file).targets;
    assertEquals(1, impls.length);
    final PsiElement meth = impls[0];
    assertTrue(meth instanceof PsiMethod);
    final PsiClass aClass = ((PsiMethod)meth).getContainingClass();
    assertNotNull(aClass);
    assertEquals(aClass.getName(), "X");
  }
}