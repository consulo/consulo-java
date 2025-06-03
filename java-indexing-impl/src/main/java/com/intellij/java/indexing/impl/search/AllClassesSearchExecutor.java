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

/*
 * @author max
 */
package com.intellij.java.indexing.impl.search;

import com.intellij.java.indexing.search.searches.AllClassesSearch;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.JavaRecursiveElementVisitor;
import com.intellij.java.language.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicatorProvider;
import consulo.application.progress.ProgressManager;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.project.DumbService;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.function.Predicate;

@ExtensionImpl
public class AllClassesSearchExecutor implements com.intellij.java.indexing.search.searches.AllClassesSearchExecutor {
    @Override
    public boolean execute(
        @Nonnull AllClassesSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super PsiClass> consumer
    ) {
        SearchScope scope = queryParameters.getScope();

        if (scope instanceof GlobalSearchScope globalSearchScope) {
            return processAllClassesInGlobalScope(globalSearchScope, queryParameters, consumer);
        }

        PsiElement[] scopeRoots = ((LocalSearchScope) scope).getScope();
        for (PsiElement scopeRoot : scopeRoots) {
            if (!processScopeRootForAllClasses(scopeRoot, consumer)) {
                return false;
            }
        }
        return true;
    }

    private static boolean processAllClassesInGlobalScope(
        @Nonnull GlobalSearchScope scope,
        @Nonnull AllClassesSearch.SearchParameters parameters,
        @Nonnull Predicate<? super PsiClass> processor
    ) {
        Set<String> names = new HashSet<>(10000);
        processClassNames(
            parameters.getProject(),
            scope,
            s -> {
                if (parameters.nameMatches(s)) {
                    names.add(s);
                }
                return true;
            }
        );

        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);

        return processClassesByNames(parameters.getProject(), scope, sorted, processor);
    }

    public static boolean processClassesByNames(
        Project project,
        GlobalSearchScope scope,
        Collection<String> names,
        Predicate<? super PsiClass> processor
    ) {
        PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);

        for (final String name : names) {
            ProgressIndicatorProvider.checkCanceled();
            if (!processByName(project, scope, processor, cache, name)) {
                return false;
            }
        }
        return true;
    }

    private static boolean processByName(Project project,
                                         GlobalSearchScope scope,
                                         Predicate<? super PsiClass> processor,
                                         PsiShortNamesCache cache,
                                         String name) {
        for (PsiClass psiClass : DumbService.getInstance(project).runReadActionInSmartMode(() -> cache.getClassesByName(name, scope))) {
            ProgressIndicatorProvider.checkCanceled();
            if (!processor.test(psiClass)) {
                return false;
            }
        }
        return true;
    }

    public static boolean processClassNames(Project project, GlobalSearchScope scope, Predicate<String> predicate) {
        boolean success = DumbService.getInstance(project).runReadActionInSmartMode(() ->
            PsiShortNamesCache.getInstance(project).processAllClassNames(s -> {
                ProgressManager.checkCanceled();
                return predicate.test(s);
            }, scope, null));

        ProgressManager.checkCanceled();
        return success;
    }

    private static boolean processScopeRootForAllClasses(
        @Nonnull PsiElement scopeRoot,
        @Nonnull Predicate<? super PsiClass> processor
    ) {
        boolean[] stopped = {false};

        JavaElementVisitor visitor = scopeRoot instanceof PsiCompiledElement
            ? new JavaRecursiveElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (!stopped[0]) {
                    super.visitElement(element);
                }
            }

            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
                stopped[0] = !processor.test(aClass);
                super.visitClass(aClass);
            }
        }
            : new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (!stopped[0]) {
                    super.visitElement(element);
                }
            }

            @Override
            public void visitClass(@Nonnull PsiClass aClass) {
                stopped[0] = !processor.test(aClass);
                super.visitClass(aClass);
            }
        };
        Application.get().runReadAction(() -> scopeRoot.accept(visitor));

        return !stopped[0];
    }
}
