// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.codeInsight;

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Predicate;

/**
 * A function that returns nullability info for a given context element
 */
@FunctionalInterface
public interface ContextNullabilityInfo {
    /**
     * An empty context nullability info that returns {@code null} for all contexts
     */
    @Nonnull
    ContextNullabilityInfo EMPTY = new ContextNullabilityInfo() {
        @Override
        public @Nullable NullabilityAnnotationInfo forContext(@Nonnull PsiElement context) {
            return null;
        }

        @Override
        public @Nonnull ContextNullabilityInfo orElse(@Nonnull ContextNullabilityInfo other) {
            return other;
        }

        @Override
        public @Nonnull ContextNullabilityInfo filtering(@Nonnull Predicate<PsiElement> contextFilter) {
            return this;
        }
    };

    /**
     * @param context context PSI element
     * @return nullability info for a given context element
     */
    @Nullable
    NullabilityAnnotationInfo forContext(@Nonnull PsiElement context);

    /**
     * @param info constant nullability info to return for all contexts
     * @return a function that returns given nullability info for all contexts
     */
    static @Nonnull ContextNullabilityInfo constant(@Nullable NullabilityAnnotationInfo info) {
        if (info == null) {
            return EMPTY;
        }
        return new ContextNullabilityInfo() {
            @Override
            public @Nonnull NullabilityAnnotationInfo forContext(@Nonnull PsiElement context) {
                return info;
            }

            @Override
            public @Nonnull ContextNullabilityInfo orElse(@Nonnull ContextNullabilityInfo other) {
                return this;
            }
        };
    }

    /**
     * @param contextFilter a predicate that determines whether nullability info is applicable to a given context
     * @return a new {@code ContextNullabilityInfo} that returns null for contexts that do not match the given predicate
     */
    default @Nonnull ContextNullabilityInfo filtering(@Nonnull Predicate<PsiElement> contextFilter) {
        return context -> contextFilter.test(context) ? forContext(context) : null;
    }

    /**
     * @return a new {@code ContextNullabilityInfo} that filters out the cast contexts.
     */
    default @Nonnull ContextNullabilityInfo disableInCast() {
        return filtering(context -> {
            PsiExpression parentExpression = PsiTreeUtil.getParentOfType(context, PsiExpression.class);
            return !(parentExpression instanceof PsiTypeCastExpression) ||
                !PsiTreeUtil.isAncestor(((PsiTypeCastExpression) parentExpression).getCastType(), context, false);
        });
    }

    /**
     * @param other a fallback context nullability info to use if this one is not applicable
     * @return a new {@code ContextNullabilityInfo} that returns the result of this one if it is applicable, or the result of the other one otherwise
     */
    default @Nonnull ContextNullabilityInfo orElse(@Nonnull ContextNullabilityInfo other) {
        if (other == EMPTY) {
            return this;
        }
        return context -> {
            NullabilityAnnotationInfo info = forContext(context);
            return info != null ? info : other.forContext(context);
        };
    }
}
