// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface DfIntType extends DfIntegralType {
  @Override
  @Nonnull
  LongRangeSet getRange();
  
  @Nonnull
  @Override
  default DfType join(@Nonnull DfType other) {
    if (!(other instanceof DfIntType)) return DfTypes.TOP;
    return DfTypes.intRange(((DfIntType)other).getRange().unite(getRange()));
  }

  @Nonnull
  @Override
  default DfType meet(@Nonnull DfType other) {
    if (other == DfTypes.TOP) return this;
    if (!(other instanceof DfIntType)) return DfTypes.BOTTOM;
    return DfTypes.intRange(((DfIntType)other).getRange().intersect(getRange()));
  }
  
  @Nonnull
  @Override
  default DfType meetRange(@Nonnull LongRangeSet range) {
    return meet(DfTypes.intRangeClamped(range));
  }

  @Nonnull
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiType.INT;
  }

  @Nullable
  @Override
  default DfType tryNegate() {
    LongRangeSet range = getRange();
    LongRangeSet res = DfIntRangeType.FULL_RANGE.subtract(range);
    return res.intersects(range) ? null : DfTypes.intRange(res);
  }

  @Nonnull
  static LongRangeSet extractRange(@Nonnull DfType type) {
    return type instanceof DfIntegralType ? ((DfIntegralType)type).getRange().intersect(DfIntRangeType.FULL_RANGE) :
           DfIntRangeType.FULL_RANGE;
  }
}
