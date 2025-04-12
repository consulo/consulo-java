// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.indexing.impl.search;

import com.intellij.java.language.impl.psi.util.JavaPsiRecordUtil;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.language.psi.search.SearchRequestCollector;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.function.Predicate;

@ExtensionImpl
public final class JavaRecordComponentSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>
    implements ReferencesSearchQueryExecutor {
    @Override
    public void processQuery(
        @Nonnull ReferencesSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super PsiReference> consumer
    ) {
        PsiElement element = queryParameters.getElementToSearch();
        if (element instanceof PsiRecordComponent recordComponent) {
            SearchScope scope = queryParameters.getEffectiveSearchScope();
            RecordNavigationInfo info = findNavigationInfo(recordComponent);
            if (info != null) {
                SearchRequestCollector optimizer = queryParameters.getOptimizer();
                optimizer.searchWord(
                    info.myName,
                    AccessRule.read(() -> info.myLightMethod.getUseScope().intersectWith(scope)),
                    true,
                    info.myLightMethod
                );

                optimizer.searchWord(
                    info.myName,
                    AccessRule.read(() -> info.myLightField.getUseScope().intersectWith(scope)),
                    true,
                    info.myLightField
                );

                PsiParameter parameter = info.myLightCompactConstructorParameter;
                if (parameter != null) {
                    optimizer.searchWord(
                        info.myName,
                        AccessRule.read(() -> new LocalSearchScope(parameter.getDeclarationScope())),
                        true,
                        parameter
                    );
                }
            }
        }
    }

    private static RecordNavigationInfo findNavigationInfo(PsiRecordComponent recordComponent) {
        return AccessRule.read(() -> {
            String name = recordComponent.getName();
            PsiClass containingClass = recordComponent.getContainingClass();
            if (containingClass == null) {
                return null;
            }

            List<PsiMethod> methods =
                ContainerUtil.filter(containingClass.findMethodsByName(name, false), m -> m.getParameterList().isEmpty());
            if (methods.size() != 1) {
                return null;
            }

            PsiField field = containingClass.findFieldByName(name, false);
            if (field == null) {
                return null;
            }

            PsiMethod compactConstructor = ContainerUtil.find(containingClass.getConstructors(), JavaPsiRecordUtil::isCompactConstructor);
            PsiParameter parameter = compactConstructor != null
                ? ContainerUtil.find(compactConstructor.getParameterList().getParameters(), p -> name.equals(p.getName()))
                : null;
            return new RecordNavigationInfo(methods.get(0), field, parameter, name);
        });
    }

    private static final class RecordNavigationInfo {
        @Nonnull
        final PsiMethod myLightMethod;
        @Nonnull
        final PsiField myLightField;
        @Nullable
        final PsiParameter myLightCompactConstructorParameter;
        @Nonnull
        final String myName;

        private RecordNavigationInfo(
            @Nonnull PsiMethod lightMethod,
            @Nonnull PsiField lightField,
            @Nullable PsiParameter parameter,
            @Nonnull String name
        ) {
            myLightMethod = lightMethod;
            myLightField = lightField;
            myLightCompactConstructorParameter = parameter;
            myName = name;
        }
    }
}
