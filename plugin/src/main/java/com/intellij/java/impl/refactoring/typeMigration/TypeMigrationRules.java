// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.java.impl.refactoring.typeMigration;

import com.intellij.java.impl.refactoring.typeMigration.rules.DisjunctionTypeConversionRule;
import com.intellij.java.impl.refactoring.typeMigration.rules.RootTypeConversionRule;
import com.intellij.java.impl.refactoring.typeMigration.rules.TypeConversionRule;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.content.scope.SearchScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LibraryScopeCache;import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Couple;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        myConversionRules.addAll(project.getApplication().getExtensionList(TypeConversionRule.class));
        addConversionRuleSettings(new MigrateGetterNameSetting());
    }

    public void addConversionDescriptor(TypeConversionRule rule) {
        myConversionRules.add(rule);
    }

    public void addConversionRuleSettings(Object settings) {
        myConversionCustomSettings.put(settings.getClass(), settings);
    }

    @SuppressWarnings("unchecked")
    public <T> T getConversionSettings(Class<T> aClass) {
        return (T)myConversionCustomSettings.get(aClass);
    }

    @Nullable
    public TypeConversionDescriptorBase findConversion(
        PsiType from,
        PsiType to,
        PsiMember member,
        PsiExpression context,
        boolean isCovariantPosition,
        TypeMigrationLabeler labeler
    ) {
        TypeConversionDescriptorBase conversion = findConversion(from, to, member, context, labeler);
        if (conversion != null) {
            return conversion;
        }

        if (isCovariantPosition) {
            if (to instanceof PsiEllipsisType ellipsisType) {
                if (TypeConversionUtil.isAssignable(ellipsisType.getComponentType(), from)) {
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
        PsiType from,
        PsiType to,
        PsiMember member,
        PsiExpression context,
        TypeMigrationLabeler labeler
    ) {
        for (TypeConversionRule descriptor : myConversionRules) {
            TypeConversionDescriptorBase conversion = descriptor.findConversion(from, to, member, context, labeler);
            if (conversion != null) {
                return conversion;
            }
        }
        return null;
    }

    public boolean shouldConvertNull(PsiType from, PsiType to, PsiExpression context) {
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
    public Couple<PsiType> bindTypeParameters(
        PsiType from,
        PsiType to,
        PsiMethod method,
        PsiExpression context,
        TypeMigrationLabeler labeler
    ) {
        for (TypeConversionRule conversionRule : myConversionRules) {
            Couple<PsiType> typePair = conversionRule.bindTypeParameters(from, to, method, context, labeler);
            if (typePair != null) {
                return typePair;
            }
        }
        return null;
    }
}
