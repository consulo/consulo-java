// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.language.codeInsight.Nullability;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a value nullability within DFA. Unlike {@link Nullability} may have more fine-grained
 * values useful during the DFA. If you have a DfaNullability value (e.g. from {@link CommonDataflow}),
 * and want to check if it's nullable, or not, it's advised to convert it to {@link Nullability} first,
 * as more values could be introduced to this enum in future.
 */
public enum DfaNullability {
    /**
     * Means: exactly null
     */
    NULL("Null", JavaAnalysisLocalize.nullabilityNull(), Nullability.NULLABLE),
    NULLABLE("Nullable", JavaAnalysisLocalize.nullabilityNullable(), Nullability.NULLABLE),
    NOT_NULL("Not-null", JavaAnalysisLocalize.nullabilityNonNull(), Nullability.NOT_NULL),
    UNKNOWN("Unknown", LocalizeValue.empty(), Nullability.UNKNOWN),
    /**
     * Means: non-stable variable declared as Nullable was checked for nullity and flushed afterwards (e.g. by unknown method call),
     * so we are unsure about its nullability anymore.
     */
    FLUSHED("Flushed", LocalizeValue.empty(), Nullability.UNKNOWN);

    @Nonnull
    private final String myInternalName;
    @Nonnull
    private final LocalizeValue myPresentationalName;
    @Nonnull
    private final Nullability myNullability;

    DfaNullability(@Nonnull String internalName, @Nonnull LocalizeValue presentationalName, @Nonnull Nullability nullability) {
        myInternalName = internalName;
        myPresentationalName = presentationalName;
        myNullability = nullability;
    }

    @Nonnull
    public String getInternalName() {
        return myInternalName;
    }

    @Nonnull
    public LocalizeValue getPresentationName() {
        return myPresentationalName;
    }

    @Nonnull
    public DfaNullability unite(@Nonnull DfaNullability other) {
        if (this == other) {
            return this;
        }
        if (this == NULL || other == NULL ||
            this == NULLABLE || other == NULLABLE) {
            return NULLABLE;
        }
        if (this == FLUSHED || other == FLUSHED) {
            return FLUSHED;
        }
        return UNKNOWN;
    }

    @Nullable
    public DfaNullability intersect(@Nonnull DfaNullability right) {
        if (this == NOT_NULL) {
            return right == NULL ? null : NOT_NULL;
        }
        if (right == NOT_NULL) {
            return this == NULL ? null : NOT_NULL;
        }
        if (this == UNKNOWN) {
            return right;
        }
        if (right == UNKNOWN) {
            return this;
        }
        if (this == FLUSHED && toNullability(right) == Nullability.NULLABLE
            || right == FLUSHED && toNullability(this) == Nullability.NULLABLE) {
            return NULLABLE;
        }
        return equals(right) ? this : null;
    }

    @Nonnull
    public static Nullability toNullability(@Nullable DfaNullability dfaNullability) {
        return dfaNullability == null ? Nullability.UNKNOWN : dfaNullability.myNullability;
    }

    @Nonnull
    public static DfaNullability fromNullability(@Nonnull Nullability nullability) {
        return switch (nullability) {
            case NOT_NULL -> NOT_NULL;
            case NULLABLE -> NULLABLE;
            case UNKNOWN -> UNKNOWN;
            default -> throw new IllegalStateException("Unknown nullability: " + nullability);
        };
    }

    @Nonnull
    public DfReferenceType asDfType() {
        return switch (this) {
            case NULL -> DfTypes.NULL;
            case NOT_NULL -> DfTypes.NOT_NULL_OBJECT;
            case UNKNOWN -> DfTypes.OBJECT_OR_NULL;
            default -> DfTypes.customObject(TypeConstraints.TOP, this, Mutability.UNKNOWN, null, DfTypes.BOTTOM);
        };
    }

    @Nonnull
    public static DfaNullability fromDfType(@Nonnull DfType type) {
        return type instanceof DfReferenceType refType ? refType.getNullability() : UNKNOWN;
    }
}
