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
package com.intellij.dependencies;

import com.intellij.JavaTestUtil;
import consulo.language.editor.scope.AnalysisScope;
import com.intellij.java.impl.analysis.JavaAnalysisScope;
import consulo.ide.impl.idea.packageDependencies.BackwardDependenciesBuilder;
import consulo.ide.impl.idea.packageDependencies.DependenciesBuilder;
import consulo.ide.impl.idea.packageDependencies.FindDependencyUtil;
import consulo.ide.impl.idea.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import consulo.usage.UsageInfo;
import com.intellij.usages.TextChunk;
import consulo.usage.Usage;
import consulo.usage.UsageInfo2UsageAdapter;

import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: Jan 18, 2005
 */
public abstract class UsagesInAnalyzingDependenciesTest extends PsiTestCase{
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    String root = JavaTestUtil.getJavaTestDataPath() + "/dependencies/search/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
  }

  public void testForwardPackageScope(){
    PsiJavaPackage bPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage("com.b");
    DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, new JavaAnalysisScope(bPackage, null));
    builder.analyze();
    Set<PsiFile> searchFor = new HashSet<PsiFile>();
    searchFor.add(myJavaFacade.findClass("com.a.A", GlobalSearchScope.allScope(myProject)).getContainingFile());
    Set<PsiFile> searchIn = new HashSet<PsiFile>();
    PsiClass bClass = myJavaFacade.findClass("com.b.B", GlobalSearchScope.allScope(myProject));
    searchIn.add(bClass.getContainingFile());
    PsiClass cClass = myJavaFacade.findClass("com.b.C", GlobalSearchScope.allScope(myProject));
    searchIn.add(cClass.getContainingFile());
    UsageInfo[] usagesInfos = FindDependencyUtil.findDependencies(builder, searchIn, searchFor);
    UsageInfo2UsageAdapter[] usages = UsageInfo2UsageAdapter.convert(usagesInfos);
    String [] psiUsages = new String [usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      psiUsages[i] = toString(usages[i]);
    }
    checkResult(new String []{
      "(2: 14) import com.a.A;",
      "(4: 3) A myA = new A();",
      "(4: 15) A myA = new A();",
      "(6: 9) myA.aa();",

      "(2: 14) import com.a.A;",
      "(4: 3) A myA = new A();",
      "(4: 15) A myA = new A();",
      "(6: 9) myA.aa();"}, psiUsages);
  }

  private static String toString(Usage usage) {
    TextChunk[] textChunks = usage.getPresentation().getText();
    StringBuffer result = new StringBuffer();
    for (TextChunk textChunk : textChunks) {
      result.append(textChunk);
    }

    return result.toString();
  }

   public void testBackwardPackageScope(){
     PsiJavaPackage bPackage = JavaPsiFacade.getInstance(myPsiManager.getProject()).findPackage("com.a");
    DependenciesBuilder builder = new BackwardDependenciesBuilder(myProject, new JavaAnalysisScope(bPackage, null));
    builder.analyze();
    Set<PsiFile> searchFor = new HashSet<PsiFile>();
    searchFor.add(myJavaFacade.findClass("com.a.A", GlobalSearchScope.allScope(myProject)).getContainingFile());
    Set<PsiFile> searchIn = new HashSet<PsiFile>();
    PsiClass bClass = myJavaFacade.findClass("com.b.B", GlobalSearchScope.allScope(myProject));
    searchIn.add(bClass.getContainingFile());
    PsiClass cClass = myJavaFacade.findClass("com.a.C", GlobalSearchScope.allScope(myProject));
    searchFor.add(cClass.getContainingFile());
    UsageInfo[] usagesInfos = FindDependencyUtil.findBackwardDependencies(builder, searchIn, searchFor);
    UsageInfo2UsageAdapter[] usages = UsageInfo2UsageAdapter.convert(usagesInfos);
    String [] psiUsages = new String [usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      psiUsages[i] = toString(usages[i]);
    }
    checkResult(new String []{"(4: 3) A myA = new A();", "(4: 15) A myA = new A();", "(5: 3) C myC = new C();", "(5: 15) C myC = new C();", "(7: 9) myA.aa();", "(8: 9) myC.cc();"}, psiUsages);
  }

  public void testForwardSimple(){
    DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, new AnalysisScope(myProject));
    builder.analyze();

    Set<PsiFile> searchIn = new HashSet<PsiFile>();
    PsiClass aClass = myJavaFacade.findClass("A", GlobalSearchScope.allScope(myProject));
    searchIn.add(aClass.getContainingFile());
    Set<PsiFile> searchFor = new HashSet<PsiFile>();
    PsiClass bClass = myJavaFacade.findClass("B", GlobalSearchScope.allScope(myProject));
    searchFor.add(bClass.getContainingFile());

    UsageInfo[] usagesInfos = FindDependencyUtil.findDependencies(builder, searchIn, searchFor);
    UsageInfo2UsageAdapter[] usages = UsageInfo2UsageAdapter.convert(usagesInfos);
    String [] psiUsages = new String [usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      psiUsages[i] = toString(usages[i]);
    }
    checkResult(new String []{"(2: 3) B myB = new B();", "(2: 15) B myB = new B();", "(4: 9) myB.bb();"}, psiUsages);
  }

  private static void checkResult(String[] usages, String [] psiUsages) {
    assertEquals(usages.length , psiUsages.length);
    for (int i = 0; i < psiUsages.length; i++) {
      assertEquals(usages[i], psiUsages[i]);
    }
  }
}
