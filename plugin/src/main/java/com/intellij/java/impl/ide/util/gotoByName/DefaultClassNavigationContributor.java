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
package com.intellij.java.impl.ide.util.gotoByName;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.CommonProcessors;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.ide.navigation.GotoClassOrTypeContributor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FindSymbolParameters;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.IdFilter;
import consulo.language.psi.util.SymbolPresentationUtil;
import consulo.navigation.NavigationItem;
import consulo.project.Project;
import consulo.project.content.scope.ProjectAwareSearchScope;
import consulo.util.collection.ArrayUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;

@ExtensionImpl
public class DefaultClassNavigationContributor implements GotoClassOrTypeContributor {
  @Override
  @Nonnull
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    if (FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping) {
      GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
      CommonProcessors.CollectProcessor<String> processor = new CommonProcessors.CollectProcessor<String>();
      processNames(processor, scope, IdFilter.getProjectIdFilter(project, includeNonProjectItems));

      return ArrayUtil.toStringArray(processor.getResults());
    }

    return PsiShortNamesCache.getInstance(project).getAllClassNames();
  }

  @Override
  @Nonnull
  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    GlobalSearchScope scope = includeNonProjectItems ? GlobalSearchScope.allScope(project) : GlobalSearchScope.projectScope(project);
    return filterUnshowable(PsiShortNamesCache.getInstance(project).getClassesByName(name, scope), pattern);
  }

  private static NavigationItem[] filterUnshowable(PsiClass[] items, String pattern) {
    boolean isAnnotation = pattern.startsWith("@");
    ArrayList<NavigationItem> list = new ArrayList<NavigationItem>(items.length);
    for (PsiClass item : items) {
      if (item.getContainingFile().getVirtualFile() == null) continue;
      if (isAnnotation && !item.isAnnotationType()) continue;
      list.add(item);
    }
    return list.toArray(new NavigationItem[list.size()]);
  }

  @Override
  public String getQualifiedName(NavigationItem item) {
    if (item instanceof PsiClass) {
      return getQualifiedNameForClass((PsiClass) item);
    }
    return null;
  }

  public static String getQualifiedNameForClass(PsiClass psiClass) {
    String qName = psiClass.getQualifiedName();
    if (qName != null) return qName;

    String containerText = SymbolPresentationUtil.getSymbolContainerText(psiClass);
    return containerText + "." + psiClass.getName();
  }

  @Override
  public String getQualifiedNameSeparator() {
    return ".";
  }

  @Override
  public void processNames(@Nonnull Processor<String> processor, @Nonnull SearchScope scope, @Nullable IdFilter filter) {
    PsiShortNamesCache.getInstance(((ProjectAwareSearchScope) scope).getProject()).processAllClassNames(processor, (GlobalSearchScope) scope, filter);
  }

  @Override
  public void processElementsWithName(@Nonnull String name,
                                      @Nonnull Processor<NavigationItem> processor,
                                      @Nonnull FindSymbolParameters parameters) {
    PsiShortNamesCache.getInstance(parameters.getProject()).processClassesWithName(name, processor, (GlobalSearchScope) parameters.getSearchScope(), parameters.getIdFilter());
  }
}