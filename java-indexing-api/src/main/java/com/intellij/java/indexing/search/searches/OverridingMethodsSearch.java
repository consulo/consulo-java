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
package com.intellij.java.indexing.search.searches;

import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.content.scope.SearchScope;
import consulo.application.util.query.ExtensibleQueryFactory;
import consulo.application.util.query.EmptyQuery;
import consulo.application.util.query.Query;

import java.util.function.Supplier;

/**
 * @author max
 */
public class OverridingMethodsSearch extends ExtensibleQueryFactory<PsiMethod, OverridingMethodsSearch.SearchParameters> {
  public static final OverridingMethodsSearch INSTANCE = new OverridingMethodsSearch();

  public static class SearchParameters {
    private final PsiMethod myMethod;
    private final SearchScope myScope;
    private final boolean myCheckDeep;

    public SearchParameters(final PsiMethod aClass, SearchScope scope, final boolean checkDeep) {
      myMethod = aClass;
      myScope = scope;
      myCheckDeep = checkDeep;
    }

    public PsiMethod getMethod() {
      return myMethod;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private OverridingMethodsSearch() {
    super(OverridingMethodsSearchExecutor.class);
  }

  public static Query<PsiMethod> search(final PsiMethod method, SearchScope scope, final boolean checkDeep) {
    if (ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> cannotBeOverriden(method))) return EmptyQuery.getEmptyQuery(); // Optimization
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(method, scope, checkDeep));
  }

  private static boolean cannotBeOverriden(final PsiMethod method) {
    final PsiClass parentClass = method.getContainingClass();
    return parentClass == null
           || method.isConstructor()
           || method.hasModifierProperty(PsiModifier.STATIC)
           || method.hasModifierProperty(PsiModifier.FINAL)
           || method.hasModifierProperty(PsiModifier.PRIVATE)
           || parentClass instanceof PsiAnonymousClass
           || parentClass.hasModifierProperty(PsiModifier.FINAL);
  }

  public static Query<PsiMethod> search(final PsiMethod method, final boolean checkDeep) {
    return search(method, ApplicationManager.getApplication().runReadAction(new Supplier<>() {
      @Override
      public SearchScope get() {
        return method.getUseScope();
      }
    }), checkDeep);
  }

  public static Query<PsiMethod> search(final PsiMethod method) {
    return search(method, true);
  }
}
