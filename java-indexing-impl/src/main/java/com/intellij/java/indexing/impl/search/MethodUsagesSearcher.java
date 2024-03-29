/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearchExecutor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.SearchRequestCollector;
import consulo.language.psi.search.UsageSearchContext;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;

/**
 * @author max
 */
@ExtensionImpl
public class MethodUsagesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> implements MethodReferencesSearchExecutor {
  @Override
  public void processQuery(@Nonnull final MethodReferencesSearch.SearchParameters p, @Nonnull final Processor<? super PsiReference> consumer) {
    final PsiMethod method = p.getMethod();
    final boolean[] isConstructor = new boolean[1];
    final PsiManager[] psiManager = new PsiManager[1];
    final String[] methodName = new String[1];
    final boolean[] isValueAnnotation = new boolean[1];
    final boolean[] needStrictSignatureSearch = new boolean[1];
    final boolean strictSignatureSearch = p.isStrictSignatureSearch();

    final PsiClass aClass = resolveInReadAction(p.getProject(), new Computable<PsiClass>() {
      @Override
      public PsiClass compute() {
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) {
          return null;
        }
        isConstructor[0] = method.isConstructor();
        psiManager[0] = aClass.getManager();
        methodName[0] = method.getName();
        isValueAnnotation[0] = PsiUtil.isAnnotationMethod(method) &&
            PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(methodName[0]) &&
            method.getParameterList().getParametersCount() == 0;
        needStrictSignatureSearch[0] = strictSignatureSearch && (aClass instanceof PsiAnonymousClass || aClass.hasModifierProperty(PsiModifier.FINAL) || method.hasModifierProperty
            (PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.FINAL) || method.hasModifierProperty(PsiModifier.PRIVATE));
        return aClass;
      }
    });
    if (aClass == null) {
      return;
    }

    final SearchRequestCollector collector = p.getOptimizer();

    final SearchScope searchScope = resolveInReadAction(p.getProject(), new Computable<SearchScope>() {
      @Override
      public SearchScope compute() {
        return p.getEffectiveSearchScope();
      }
    });
    if (searchScope == GlobalSearchScope.EMPTY_SCOPE) {
      return;
    }

    if (isConstructor[0]) {
      new ConstructorReferencesSearchHelper(psiManager[0]).
          processConstructorReferences(consumer, method, aClass, searchScope, p.getProject(), false, strictSignatureSearch, collector);
    }

    if (isValueAnnotation[0]) {
      Processor<PsiReference> refProcessor = PsiAnnotationMethodReferencesSearcher.createImplicitDefaultAnnotationMethodConsumer(consumer);
      ReferencesSearch.search(aClass, searchScope).forEach(refProcessor);
    }

    if (needStrictSignatureSearch[0]) {
      ReferencesSearch.searchOptimized(method, searchScope, false, collector, consumer);
      return;
    }

    if (StringUtil.isEmpty(methodName[0])) {
      return;
    }

    resolveInReadAction(p.getProject(), new Computable<Void>() {
      @Override
      public Void compute() {
        final PsiMethod[] methods = strictSignatureSearch ? new PsiMethod[]{method} : aClass.findMethodsByName(methodName[0], false);
        SearchScope accessScope = methods[0].getUseScope();
        for (int i = 1; i < methods.length; i++) {
          PsiMethod method1 = methods[i];
          accessScope = accessScope.union(method1.getUseScope());
        }

        SearchScope restrictedByAccessScope = searchScope.intersectWith(accessScope);

        short searchContext = UsageSearchContext.IN_CODE | UsageSearchContext.IN_COMMENTS | UsageSearchContext.IN_FOREIGN_LANGUAGES;
        collector.searchWord(methodName[0], restrictedByAccessScope, searchContext, true, method, getTextOccurrenceProcessor(methods, aClass, strictSignatureSearch));

        SimpleAccessorReferenceSearcher.addPropertyAccessUsages(method, restrictedByAccessScope, collector);
        return null;
      }
    });
  }

  static <T> T resolveInReadAction(@Nonnull Project p, @Nonnull Computable<T> computable) {
    return ApplicationManager.getApplication().isReadAccessAllowed() ? computable.compute() : DumbService.getInstance(p).runReadActionInSmartMode(computable);
  }

  protected MethodTextOccurrenceProcessor getTextOccurrenceProcessor(PsiMethod[] methods, PsiClass aClass, boolean strictSignatureSearch) {
    return new MethodTextOccurrenceProcessor(aClass, strictSignatureSearch, methods);
  }
}
