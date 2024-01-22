// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.RelationType;
import jakarta.annotation.Nonnull;

/**
 * Represents an integral primitive (int or long)
 */
public interface DfIntegralType extends DfPrimitiveType {
  @jakarta.annotation.Nonnull
  LongRangeSet getRange();

  @jakarta.annotation.Nonnull
  default DfType meetRelation(@Nonnull RelationType relation, @jakarta.annotation.Nonnull DfType other) {
    if (other == DfTypes.TOP) return this;
    if (other instanceof DfIntegralType) {
      return meetRange(((DfIntegralType)other).getRange().fromRelation(relation));
    }
    return DfTypes.BOTTOM;
  }

  @jakarta.annotation.Nonnull
  DfType meetRange(@jakarta.annotation.Nonnull LongRangeSet range);
}
