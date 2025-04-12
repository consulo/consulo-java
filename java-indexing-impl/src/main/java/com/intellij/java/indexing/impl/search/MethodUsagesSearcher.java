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
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
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

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author max
 */
@ExtensionImpl
public class MethodUsagesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>
    implements MethodReferencesSearchExecutor {
    @Override
    public void processQuery(
        @Nonnull MethodReferencesSearch.SearchParameters p,
        @Nonnull Predicate<? super PsiReference> consumer
    ) {
        PsiMethod method = p.getMethod();
        boolean[] isConstructor = new boolean[1];
        PsiManager[] psiManager = new PsiManager[1];
        String[] methodName = new String[1];
        boolean[] isValueAnnotation = new boolean[1];
        boolean[] needStrictSignatureSearch = new boolean[1];
        boolean strictSignatureSearch = p.isStrictSignatureSearch();

        PsiClass aClass = resolveInReadAction(
            p.getProject(),
            () -> {
                PsiClass aClass1 = method.getContainingClass();
                if (aClass1 == null) {
                    return null;
                }
                isConstructor[0] = method.isConstructor();
                psiManager[0] = aClass1.getManager();
                methodName[0] = method.getName();
                isValueAnnotation[0] = PsiUtil.isAnnotationMethod(method)
                    && PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(methodName[0])
                    && method.getParameterList().getParametersCount() == 0;
                needStrictSignatureSearch[0] = strictSignatureSearch
                    && (aClass1 instanceof PsiAnonymousClass
                    || aClass1.isFinal() || method.isStatic() || method.isFinal() || method.isPrivate());
                return aClass1;
            }
        );
        if (aClass == null) {
            return;
        }

        SearchRequestCollector collector = p.getOptimizer();

        SearchScope searchScope = resolveInReadAction(p.getProject(), p::getEffectiveSearchScope);
        if (searchScope == GlobalSearchScope.EMPTY_SCOPE) {
            return;
        }

        if (isConstructor[0]) {
            new ConstructorReferencesSearchHelper(psiManager[0]).processConstructorReferences(
                consumer,
                method,
                aClass,
                searchScope,
                p.getProject(),
                false,
                strictSignatureSearch,
                collector
            );
        }

        if (isValueAnnotation[0]) {
            Predicate<PsiReference> refProcessor =
                PsiAnnotationMethodReferencesSearcher.createImplicitDefaultAnnotationMethodConsumer(consumer);
            ReferencesSearch.search(aClass, searchScope).forEach(refProcessor);
        }

        if (needStrictSignatureSearch[0]) {
            ReferencesSearch.searchOptimized(method, searchScope, false, collector, consumer);
            return;
        }

        if (StringUtil.isEmpty(methodName[0])) {
            return;
        }

        resolveInReadAction(
            p.getProject(),
            (Supplier<Void>)() -> {
                PsiMethod[] methods =
                    strictSignatureSearch ? new PsiMethod[]{method} : aClass.findMethodsByName(methodName[0], false);
                SearchScope accessScope = methods[0].getUseScope();
                for (int i = 1; i < methods.length; i++) {
                    PsiMethod method1 = methods[i];
                    accessScope = accessScope.union(method1.getUseScope());
                }

                SearchScope restrictedByAccessScope = searchScope.intersectWith(accessScope);

                short searchContext = UsageSearchContext.IN_CODE | UsageSearchContext.IN_COMMENTS | UsageSearchContext.IN_FOREIGN_LANGUAGES;
                collector.searchWord(
                    methodName[0],
                    restrictedByAccessScope,
                    searchContext,
                    true,
                    method,
                    getTextOccurrenceProcessor(methods, aClass, strictSignatureSearch)
                );

                SimpleAccessorReferenceSearcher.addPropertyAccessUsages(method, restrictedByAccessScope, collector);
                return null;
            }
        );
    }

    static <T> T resolveInReadAction(@Nonnull Project p, @Nonnull Supplier<T> computable) {
        return Application.get().isReadAccessAllowed()
            ? computable.get()
            : DumbService.getInstance(p).runReadActionInSmartMode(computable);
    }

    protected MethodTextOccurrenceProcessor getTextOccurrenceProcessor(
        PsiMethod[] methods,
        PsiClass aClass,
        boolean strictSignatureSearch
    ) {
        return new MethodTextOccurrenceProcessor(aClass, strictSignatureSearch, methods);
    }
}
