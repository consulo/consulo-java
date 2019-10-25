// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a value nullability within DFA. Unlike {@link Nullability} may have more fine-grained
 * values useful during the DFA. If you have a DfaNullability value (e.g. from {@link CommonDataflow}),
 * and want to check if it's nullable, or not, it's advised to convert it to {@link Nullability} first,
 * as more values could be introduced to this enum in future.
 *
 * @see DfaFactType#NULLABILITY
 */
public enum DfaNullability {
  /**
   * Means: exactly null
   */
  NULL("Null", "null", Nullability.NULLABLE),
  NULLABLE("Nullable", "nullable", Nullability.NULLABLE),
  NOT_NULL("Not-null", "non-null", Nullability.NOT_NULL),
  UNKNOWN("Unknown", "", Nullability.UNKNOWN),
  /**
   * Means: non-stable variable declared as Nullable was checked for nullity and flushed afterwards (e.g. by unknown method call),
   * so we are unsure about its nullability anymore.
   */
  FLUSHED("Flushed", "", Nullability.UNKNOWN);

  private final @Nonnull
  String myInternalName;
  private final @Nonnull
  String myPresentationalName;
  private final @Nonnull
  Nullability myNullability;

  DfaNullability(@Nonnull String internalName, @Nonnull String presentationalName, @Nonnull Nullability nullability) {
    myInternalName = internalName;
    myPresentationalName = presentationalName;
    myNullability = nullability;
  }

  @Nonnull
  public String getInternalName() {
    return myInternalName;
  }

  @Nonnull
  public String getPresentationName() {
    return myPresentationalName;
  }

  public static boolean isNullable(DfaFactMap map) {
    return toNullability(map.get(DfaFactType.NULLABILITY)) == Nullability.NULLABLE;
  }

  public static boolean isNotNull(DfaFactMap map) {
    return map.get(DfaFactType.NULLABILITY) == NOT_NULL;
  }

  @Nonnull
  public static Nullability toNullability(@Nullable DfaNullability dfaNullability) {
    return dfaNullability == null ? Nullability.UNKNOWN : dfaNullability.myNullability;
  }

  @Nonnull
  public static DfaNullability fromNullability(@Nonnull Nullability nullability) {
    switch (nullability) {
      case NOT_NULL:
        return NOT_NULL;
      case NULLABLE:
        return NULLABLE;
      case UNKNOWN:
        return UNKNOWN;
    }
    throw new IllegalStateException("Unknown nullability: "+nullability);
  }
}
