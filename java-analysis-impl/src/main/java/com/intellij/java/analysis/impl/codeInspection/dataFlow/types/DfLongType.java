// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import org.jspecify.annotations.Nullable;

public interface DfLongType extends DfIntegralType {
  @Override
  LongRangeSet getRange();

  @Override
  default DfType join(DfType other) {
    if (!(other instanceof DfLongType)) return DfTypes.TOP;
    return DfTypes.longRange(((DfLongType)other).getRange().unite(getRange()));
  }

  @Override
  default DfType meet(DfType other) {
    if (other == DfTypes.TOP) return this;
    if (!(other instanceof DfLongType)) return DfTypes.BOTTOM;
    return DfTypes.longRange(((DfLongType)other).getRange().intersect(getRange()));
  }

  @Override
  default DfType meetRange(LongRangeSet range) {
    return meet(DfTypes.longRange(range));
  }

  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiType.LONG;
  }

  @Override
  @Nullable
  default DfType tryNegate() {
    LongRangeSet range = getRange();
    LongRangeSet res = LongRangeSet.all().subtract(range);
    return res.intersects(range) ? null : DfTypes.longRange(res);
  }

  static LongRangeSet extractRange(DfType type) {
    return type instanceof DfIntegralType ? ((DfIntegralType)type).getRange() : LongRangeSet.all();
  }
}
