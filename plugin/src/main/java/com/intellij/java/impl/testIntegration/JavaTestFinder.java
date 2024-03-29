/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.impl.testIntegration;

import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import consulo.annotation.component.ExtensionImpl;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.util.lang.Pair;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.editor.testIntegration.TestFinder;
import consulo.language.editor.testIntegration.TestFinderHelper;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@ExtensionImpl
public class JavaTestFinder implements TestFinder {
  public PsiClass findSourceElement(@Nonnull PsiElement element) {
    return TestIntegrationUtils.findOuterClass(element);
  }

  @Nonnull
  public Collection<PsiElement> findClassesForTest(@Nonnull PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) {
      return Collections.emptySet();
    }

    GlobalSearchScope scope;
    Module module = getModule(element);
    if (module != null) {
      scope = GlobalSearchScope.moduleWithDependenciesScope(module);
    } else {
      scope = GlobalSearchScope.projectScope(element.getProject());
    }

    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(element.getProject());

    List<Pair<? extends PsiNamedElement, Integer>> classesWithWeights = new ArrayList<Pair<? extends PsiNamedElement, Integer>>();
    for (Pair<String, Integer> eachNameWithWeight : TestFinderHelper.collectPossibleClassNamesWithWeights(klass.getName())) {
      for (PsiClass eachClass : cache.getClassesByName(eachNameWithWeight.first, scope)) {
        if (isTestSubjectClass(eachClass)) {
          classesWithWeights.add(Pair.create(eachClass, eachNameWithWeight.second));
        }
      }
    }

    return TestFinderHelper.getSortedElements(classesWithWeights, false);
  }

  private static boolean isTestSubjectClass(PsiClass klass) {
    if (klass.isAnnotationType() || TestFrameworks.getInstance().isTestClass(klass)) {
      return false;
    }
    return true;
  }

  @Nonnull
  public Collection<PsiElement> findTestsForClass(@Nonnull PsiElement element) {
    PsiClass klass = findSourceElement(element);
    if (klass == null) {
      return Collections.emptySet();
    }

    GlobalSearchScope scope;
    Module module = getModule(element);
    if (module != null) {
      scope = GlobalSearchScope.moduleWithDependentsScope(module);
    } else {
      scope = GlobalSearchScope.projectScope(element.getProject());
    }

    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(element.getProject());

    String klassName = klass.getName();
    Pattern pattern = Pattern.compile(".*" + klassName + ".*");

    List<Pair<? extends PsiNamedElement, Integer>> classesWithProximities = new ArrayList<Pair<? extends PsiNamedElement, Integer>>();

    cache.processAllClassNames(eachName ->
    {
      if (pattern.matcher(eachName).matches()) {
        for (PsiClass eachClass : cache.getClassesByName(eachName, scope)) {
          if (TestFrameworks.getInstance().isTestClass(eachClass)) {
            classesWithProximities.add(Pair.create(eachClass, TestFinderHelper.calcTestNameProximity(klassName, eachName)));
          }
        }
      }
      return true;
    });

    return TestFinderHelper.getSortedElements(classesWithProximities, true);
  }

  @Nullable
  private static Module getModule(PsiElement element) {
    ProjectFileIndex index = ProjectRootManager.getInstance(element.getProject()).getFileIndex();
    return index.getModuleForFile(element.getContainingFile().getVirtualFile());
  }

  public boolean isTest(@Nonnull PsiElement element) {
    return TestIntegrationUtils.isTest(element);
  }
}
