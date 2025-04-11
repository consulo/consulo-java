// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.java.impl.refactoring.typeMigration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import org.jetbrains.annotations.NonNls;
import consulo.project.Project;
import consulo.ide.impl.idea.openapi.roots.impl.LibraryScopeCache;
import consulo.util.lang.Pair;
import com.intellij.java.language.psi.PsiEllipsisType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.content.scope.SearchScope;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import com.intellij.java.impl.refactoring.typeMigration.rules.DisjunctionTypeConversionRule;
import com.intellij.java.impl.refactoring.typeMigration.rules.RootTypeConversionRule;
import com.intellij.java.impl.refactoring.typeMigration.rules.TypeConversionRule;
import consulo.util.collection.ContainerUtil;

/**
 * @author db
 */
public class TypeMigrationRules {
    private final List<TypeConversionRule> myConversionRules;
    private final Map<Class, Object> myConversionCustomSettings = new HashMap<>();
    private final Project myProject;
    private SearchScope mySearchScope;

    public TypeMigrationRules(@Nonnull Project project) {
        myProject = project;
        TypeConversionRule[] extensions = TypeConversionRule.EP_NAME.getExtensions();
        myConversionRules = new ArrayList<>(extensions.length + 2);
        myConversionRules.add(new RootTypeConversionRule());
        myConversionRules.add(new DisjunctionTypeConversionRule());
        ContainerUtil.addAll(myConversionRules, extensions);
        addConversionRuleSettings(new MigrateGetterNameSetting());
    }

    public void addConversionDescriptor(TypeConversionRule rule) {
        myConversionRules.add(rule);
    }

    public void addConversionRuleSettings(Object settings) {
        myConversionCustomSettings.put(settings.getClass(), settings);
    }

    public <T> T getConversionSettings(Class<T> aClass) {
        return (T)myConversionCustomSettings.get(aClass);
    }

    @NonNls
    @Nullable
    public TypeConversionDescriptorBase findConversion(
        final PsiType from,
        final PsiType to,
        final PsiMember member,
        final PsiExpression context,
        final boolean isCovariantPosition,
        final TypeMigrationLabeler labeler
    ) {
        final TypeConversionDescriptorBase conversion = findConversion(from, to, member, context, labeler);
        if (conversion != null) {
            return conversion;
        }

        if (isCovariantPosition) {
            if (to instanceof PsiEllipsisType) {
                if (TypeConversionUtil.isAssignable(((PsiEllipsisType)to).getComponentType(), from)) {
                    return new TypeConversionDescriptorBase();
                }
            }
            if (TypeConversionUtil.isAssignable(to, from)) {
                return new TypeConversionDescriptorBase();
            }
        }

        return !isCovariantPosition && TypeConversionUtil.isAssignable(from, to) ? new TypeConversionDescriptorBase() : null;
    }

    @Nullable
    public TypeConversionDescriptorBase findConversion(
        final PsiType from,
        final PsiType to,
        final PsiMember member,
        final PsiExpression context,
        final TypeMigrationLabeler labeler
    ) {
        for (TypeConversionRule descriptor : myConversionRules) {
            final TypeConversionDescriptorBase conversion = descriptor.findConversion(from, to, member, context, labeler);
            if (conversion != null) {
                return conversion;
            }
        }
        return null;
    }

    public boolean shouldConvertNull(final PsiType from, final PsiType to, PsiExpression context) {
        return myConversionRules.stream().anyMatch(rule -> rule.shouldConvertNullInitializer(from, to, context));
    }

    public void setBoundScope(@Nonnull SearchScope searchScope) {
        mySearchScope =
            searchScope.intersectWith(GlobalSearchScope.notScope(LibraryScopeCache.getInstance(myProject).getLibrariesOnlyScope()));
    }

    public SearchScope getSearchScope() {
        return mySearchScope;
    }

    @Nullable
    public Pair<PsiType, PsiType> bindTypeParameters(
        final PsiType from,
        final PsiType to,
        final PsiMethod method,
        final PsiExpression context,
        final TypeMigrationLabeler labeler
    ) {
        for (TypeConversionRule conversionRule : myConversionRules) {
            final Pair<PsiType, PsiType> typePair = conversionRule.bindTypeParameters(from, to, method, context, labeler);
            if (typePair != null) {
                return typePair;
            }
        }
        return null;
    }
}
