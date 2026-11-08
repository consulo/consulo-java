// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfReferenceType;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.analysis.JavaAnalysisBundle;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

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
  NULL("Null", JavaAnalysisBundle.messagePointer("nullability.null"), Nullability.NULLABLE),
  NULLABLE("Nullable", JavaAnalysisBundle.messagePointer("nullability.nullable"), Nullability.NULLABLE),
  NOT_NULL("Not-null", JavaAnalysisBundle.messagePointer("nullability.non.null"), Nullability.NOT_NULL),
  UNKNOWN("Unknown", () -> "", Nullability.UNKNOWN),
  /**
   * Means: non-stable variable declared as Nullable was checked for nullity and flushed afterwards (e.g. by unknown method call),
   * so we are unsure about its nullability anymore.
   */
  FLUSHED("Flushed", () -> "", Nullability.UNKNOWN);

  private final String myInternalName;
  private final Supplier<String> myPresentationalName;
  private final Nullability myNullability;

  DfaNullability(String internalName, Supplier<String> presentationalName, Nullability nullability) {
    myInternalName = internalName;
    myPresentationalName = presentationalName;
    myNullability = nullability;
  }

  public String getInternalName() {
    return myInternalName;
  }

  public String getPresentationName() {
    return myPresentationalName.get();
  }

  public DfaNullability unite(DfaNullability other) {
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

  public @Nullable DfaNullability intersect(DfaNullability right) {
    if (this == NOT_NULL) {
      return right == NULL ? null : NOT_NULL;
    }
    if (right == NOT_NULL) {
      return this == NULL ? null : NOT_NULL;
    }
    if (this == UNKNOWN) return right;
    if (right == UNKNOWN) return this;
    if (this == FLUSHED && toNullability(right) == Nullability.NULLABLE ||
        right == FLUSHED && toNullability(this) == Nullability.NULLABLE) {
      return NULLABLE;
    }
    return equals(right) ? this : null;
  }

  public static Nullability toNullability(@Nullable DfaNullability dfaNullability) {
    return dfaNullability == null ? Nullability.UNKNOWN : dfaNullability.myNullability;
  }

  public static DfaNullability fromNullability(Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> NOT_NULL;
      case NULLABLE -> NULLABLE;
      case UNKNOWN -> UNKNOWN;
    };
  }

  public DfReferenceType asDfType() {
    return switch (this) {
      case NULL -> DfTypes.NULL;
      case NOT_NULL -> DfTypes.NOT_NULL_OBJECT;
      case UNKNOWN -> DfTypes.OBJECT_OR_NULL;
      default -> DfTypes.customObject(TypeConstraints.TOP, this, Mutability.UNKNOWN, null, DfType.BOTTOM);
    };
  }

  public static DfaNullability fromDfType(DfType type) {
    return type == DfType.FAIL || type instanceof DfPrimitiveType ? NOT_NULL : 
           type instanceof DfReferenceType ? ((DfReferenceType)type).getNullability() : UNKNOWN;
  }
}
