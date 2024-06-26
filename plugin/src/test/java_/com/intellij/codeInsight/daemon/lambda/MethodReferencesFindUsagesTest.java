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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiReference;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

/**
 * User: anna
 * Date: 10/15/12
 */
public abstract class MethodReferencesFindUsagesTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/methodRef/findUsages/";
  
  

  public void testConstructorUsages() throws Exception {
    final String testName = getTestName(false);
    configureByFile(BASE_PATH + testName + ".java");

    final PsiClass aClass = getJavaFacade().findClass(testName);
    assertNotNull(aClass);
    final PsiMethod[] constructors = aClass.getConstructors();
    assertEquals(constructors.length, 1);
    Collection<PsiReference> references = MethodReferencesSearch.search(constructors[0]).findAll();
    assertEquals(1, references.size());
  }
}
