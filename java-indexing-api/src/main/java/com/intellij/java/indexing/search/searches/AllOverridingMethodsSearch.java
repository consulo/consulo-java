/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import consulo.util.lang.Pair;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.content.scope.SearchScope;
import consulo.application.util.query.ExtensibleQueryFactory;
import consulo.language.psi.PsiUtilCore;
import consulo.application.util.query.EmptyQuery;
import consulo.application.util.query.Query;

/**
 * @author ven
 * Searches deeply for all overriding methods of all methods in a class, processing pairs
 * (method in original class, overriding method)
 */
public class AllOverridingMethodsSearch extends ExtensibleQueryFactory<Pair<PsiMethod, PsiMethod>, AllOverridingMethodsSearch.SearchParameters> {
  public static final AllOverridingMethodsSearch INSTANCE = new AllOverridingMethodsSearch();

  public static class SearchParameters {
    private final PsiClass myClass;
    private final SearchScope myScope;

    public SearchParameters(final PsiClass aClass, SearchScope scope) {
      myClass = aClass;
      myScope = scope;
    }

    public PsiClass getPsiClass() {
      return myClass;
    }

    public SearchScope getScope() {
      return myScope;
    }
  }

  private AllOverridingMethodsSearch() {
    super(AllOverridingMethodsSearchExecutor.class);
  }

  public static Query<Pair<PsiMethod, PsiMethod>> search(final PsiClass aClass, SearchScope scope) {
    if (aClass.hasModifierProperty(PsiModifier.FINAL)) {
      return EmptyQuery.getEmptyQuery(); // Optimization
    }
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope));
  }

  public static Query<Pair<PsiMethod, PsiMethod>> search(final PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(aClass)));
  }
}
