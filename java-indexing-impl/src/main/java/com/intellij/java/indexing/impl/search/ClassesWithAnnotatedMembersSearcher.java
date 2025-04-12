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

import com.intellij.java.indexing.search.searches.AnnotatedElementsSearch;
import com.intellij.java.indexing.search.searches.ClassesWithAnnotatedMembersSearch;
import com.intellij.java.indexing.search.searches.ClassesWithAnnotatedMembersSearchExecutor;
import com.intellij.java.indexing.search.searches.ScopedQueryExecutor;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import consulo.application.Application;
import consulo.application.util.query.QueryExecutor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.util.query.QueryExecutorBase;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author yole
 */
@ExtensionImpl
public class ClassesWithAnnotatedMembersSearcher extends QueryExecutorBase<PsiClass, ClassesWithAnnotatedMembersSearch.Parameters>
    implements ClassesWithAnnotatedMembersSearchExecutor {
    @Override
    public void processQuery(
        @Nonnull ClassesWithAnnotatedMembersSearch.Parameters queryParameters,
        @Nonnull Predicate<? super PsiClass> consumer
    ) {
        SearchScope scope = queryParameters.getScope();
        for (QueryExecutor executor : Application.get().getExtensionList(ClassesWithAnnotatedMembersSearchExecutor.class)) {
            if (executor instanceof ScopedQueryExecutor scopedQueryExecutor) {
                scope = scope.intersectWith(GlobalSearchScope.notScope(scopedQueryExecutor.getScope(queryParameters)));
            }
        }

        Set<PsiClass> processed = new HashSet<>();
        AnnotatedElementsSearch.searchPsiMembers(queryParameters.getAnnotationClass(), scope).forEach(member ->
        {
            PsiClass psiClass = AccessRule.read(() -> member instanceof PsiClass clazz ? clazz : member.getContainingClass());

            if (psiClass != null && processed.add(psiClass)) {
                consumer.test(psiClass);
            }

            return true;
        });
    }
}
