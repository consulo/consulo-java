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
package com.intellij.psi.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.intellij.JavaTestUtil;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiReference;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;

public abstract class FindUsages15Test extends PsiTestCase{

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    //LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    String root = JavaTestUtil.getJavaTestDataPath() + "/psi/search/findUsages15/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17("java 1.5"));
    PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
  }

  public void testEnumConstructor() throws Exception {
    PsiClass enumClass = myJavaFacade.findClass("pack.OurEnum", GlobalSearchScope.moduleScope(myModule));
    assertNotNull(enumClass);
    assertTrue(enumClass.isEnum());
    PsiMethod[] constructors = enumClass.getConstructors();
    assertEquals(2, constructors.length);
    PsiReference[] references0 =
      ReferencesSearch.search(constructors[0], GlobalSearchScope.moduleScope(myModule), false).toArray(new PsiReference[0]);
    assertEquals(2, references0.length);
    assertTrue(references0[0].getElement() instanceof PsiEnumConstant);
    assertTrue(references0[1].getElement() instanceof PsiEnumConstant);
    PsiReference[] references1 =
      ReferencesSearch.search(constructors[1], GlobalSearchScope.moduleScope(myModule), false).toArray(new PsiReference[0]);
    assertEquals(1, references1.length);
    assertTrue(references1[0].getElement() instanceof PsiEnumConstant);
  }

  public void testGenericMethodOverriderUsages () throws Exception {
    PsiClass baseClass = myJavaFacade.findClass("pack.GenericClass", GlobalSearchScope.moduleScope(myModule));
    assertNotNull(baseClass);
    PsiMethod method = baseClass.getMethods()[0];
    PsiReference[] references =
      MethodReferencesSearch.search(method, GlobalSearchScope.moduleScope(myModule), false).toArray(PsiReference.EMPTY_ARRAY);
    assertEquals(1, references.length);
    PsiElement element = references[0].getElement();
    PsiClass refClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    assertEquals(refClass.getName(), "GenericClassDerived");
  }

  public void testFindRawOverriddenUsages () throws Exception {
    PsiClass baseClass = myJavaFacade.findClass("pack.Base", GlobalSearchScope.moduleScope(myModule));
    assertNotNull(baseClass);
    PsiMethod method = baseClass.getMethods()[0];
    PsiMethod[] overriders =
      OverridingMethodsSearch.search(method, GlobalSearchScope.moduleScope(myModule), true).toArray(PsiMethod.EMPTY_ARRAY);
    assertEquals(1, overriders.length);
  }
  public void testGenericOverride() throws Exception {
    PsiClass baseClass = myJavaFacade.findClass("pack.Gen", GlobalSearchScope.moduleScope(myModule));
    assertNotNull(baseClass);
    PsiMethod method = baseClass.getMethods()[0];
    PsiReference[] references =
      MethodReferencesSearch.search(method, GlobalSearchScope.projectScope(getProject()), true).toArray(PsiReference.EMPTY_ARRAY);

    assertEquals(1, references.length);

    PsiClass refClass = PsiTreeUtil.getParentOfType(references[0].getElement(), PsiClass.class);
    assertEquals("X2", refClass.getName());
  }
}
