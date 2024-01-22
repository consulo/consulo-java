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
package com.intellij.java.indexing.impl.search;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PropertyUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.language.psi.search.SearchRequestCollector;
import consulo.language.psi.search.UsageSearchContext;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
@ExtensionImpl
public class SimpleAccessorReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> implements ReferencesSearchQueryExecutor {

  public SimpleAccessorReferenceSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@jakarta.annotation.Nonnull ReferencesSearch.SearchParameters queryParameters, @Nonnull Processor<? super PsiReference> consumer) {
    PsiElement refElement = queryParameters.getElementToSearch();
    if (!(refElement instanceof PsiMethod)) return;

    addPropertyAccessUsages((PsiMethod)refElement, queryParameters.getEffectiveSearchScope(), queryParameters.getOptimizer());
  }

  static void addPropertyAccessUsages(PsiMethod method, SearchScope scope, SearchRequestCollector collector) {
    final String propertyName = PropertyUtil.getPropertyName(method);
    if (StringUtil.isNotEmpty(propertyName)) {
      SearchScope additional = GlobalSearchScope.EMPTY_SCOPE;
      for (CustomPropertyScopeProvider provider : CustomPropertyScopeProvider.EP_NAME.getExtensionList()) {
        additional = additional.union(provider.getScope(method.getProject()));
      }
      assert propertyName != null;
      final SearchScope propScope = scope.intersectWith(method.getUseScope()).intersectWith(additional);
      collector.searchWord(propertyName, propScope, UsageSearchContext.IN_FOREIGN_LANGUAGES, true, method);
    }
  }
}
