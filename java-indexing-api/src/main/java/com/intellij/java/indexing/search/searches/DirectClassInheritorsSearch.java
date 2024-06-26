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
package com.intellij.java.indexing.search.searches;

import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.util.query.ExtensibleQueryFactory;
import consulo.application.util.query.FilteredQuery;
import consulo.application.util.query.Query;
import consulo.content.scope.SearchScope;
import consulo.language.psi.scope.GlobalSearchScope;

/**
 * @author max
 */
public class DirectClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  public static DirectClassInheritorsSearch INSTANCE = new DirectClassInheritorsSearch();

  public static class SearchParameters {
    private final PsiClass myClass;
    private final SearchScope myScope;
    private final boolean myIncludeAnonymous;
    private final boolean myCheckInheritance;

    public SearchParameters(PsiClass aClass, SearchScope scope, boolean includeAnonymous, boolean checkInheritance) {
      myClass = aClass;
      myScope = scope;
      myIncludeAnonymous = includeAnonymous;
      myCheckInheritance = checkInheritance;
    }

    public SearchParameters(final PsiClass aClass, SearchScope scope, final boolean includeAnonymous) {
      this(aClass, scope, includeAnonymous, true);
    }

    public SearchParameters(final PsiClass aClass, final SearchScope scope) {
      this(aClass, scope, true);
    }

    public PsiClass getClassToProcess() {
      return myClass;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public boolean isCheckInheritance() {
      return myCheckInheritance;
    }

    public boolean includeAnonymous() {
      return myIncludeAnonymous;
    }
  }

  private DirectClassInheritorsSearch() {
    super(DirectClassInheritorsSearchExecutor.class);
  }

  public static Query<PsiClass> search(final PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(aClass.getProject()));
  }

  public static Query<PsiClass> search(final PsiClass aClass, SearchScope scope) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope));
  }

  public static Query<PsiClass> search(final PsiClass aClass, SearchScope scope, boolean includeAnonymous) {
    return search(aClass, scope, includeAnonymous, true);
  }

  public static Query<PsiClass> search(final PsiClass aClass, SearchScope scope, boolean includeAnonymous, final boolean checkInheritance) {
    final Query<PsiClass> raw = INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope, includeAnonymous, checkInheritance));

    if (!includeAnonymous) {
      return new FilteredQuery<PsiClass>(raw, psiClass -> !(psiClass instanceof PsiAnonymousClass));
    }

    return raw;
  }
}
