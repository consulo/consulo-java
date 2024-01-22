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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiFunctionalExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.application.util.query.EmptyQuery;
import consulo.application.util.query.ExtensibleQueryFactory;
import consulo.application.util.query.Query;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.PsiSearchHelper;

import jakarta.annotation.Nonnull;

public class FunctionalExpressionSearch extends ExtensibleQueryFactory<PsiFunctionalExpression, FunctionalExpressionSearch.SearchParameters> {
  public static final FunctionalExpressionSearch INSTANCE = new FunctionalExpressionSearch();

  public static class SearchParameters {
    private final PsiClass myElementToSearch;
    private final SearchScope myScope;

    public SearchParameters(@Nonnull PsiClass aClass, @Nonnull SearchScope scope) {
      myElementToSearch = aClass;
      myScope = scope;
    }

    public PsiClass getElementToSearch() {
      return myElementToSearch;
    }

    @jakarta.annotation.Nonnull
    public SearchScope getEffectiveSearchScope() {
      SearchScope accessScope = PsiSearchHelper.getInstance(myElementToSearch.getProject()).getUseScope(myElementToSearch);
      return myScope.intersectWith(accessScope);
    }
  }

  public FunctionalExpressionSearch() {
    super(FunctionalExpressionSearchExecutor.class);
  }

  public static Query<PsiFunctionalExpression> search(@Nonnull final PsiClass aClass, @jakarta.annotation.Nonnull SearchScope scope) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(aClass, scope));
  }

  public static Query<PsiFunctionalExpression> search(@Nonnull final PsiMethod psiMethod) {
    return search(psiMethod, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(psiMethod)));
  }

  public static Query<PsiFunctionalExpression> search(@Nonnull final PsiMethod psiMethod, @Nonnull final SearchScope scope) {
    return ApplicationManager.getApplication().runReadAction((Computable<Query<PsiFunctionalExpression>>) () -> {
      if (!psiMethod.hasModifierProperty(PsiModifier.STATIC) && !psiMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
        final PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass != null) {
          return INSTANCE.createUniqueResultsQuery(new SearchParameters(containingClass, scope));
        }
      }

      return EmptyQuery.getEmptyQuery();
    });
  }

  public static Query<PsiFunctionalExpression> search(@Nonnull final PsiClass aClass) {
    return search(aClass, GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(aClass)));
  }
}
