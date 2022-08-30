// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiType;
import javax.annotation.Nonnull;

class DfLongRangeType implements DfLongType {
  private final LongRangeSet myRange;

  DfLongRangeType(LongRangeSet range) {
    myRange = range;
  }

  @Nonnull
  @Override
  public LongRangeSet getRange() {
    return myRange;
  }

  @Override
  public boolean isSuperType(@Nonnull DfType other) {
    if (other == DfTypes.BOTTOM) return true;
    if (!(other instanceof DfLongType)) return false;
    return myRange.contains(((DfLongType)other).getRange());
  }

  @Override
  public int hashCode() {
    return myRange.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof DfLongRangeType && ((DfLongRangeType)obj).myRange.equals(myRange);
  }

  @Override
  public String toString() {
    if (myRange == LongRangeSet.all()) return PsiKeyword.LONG;
    return PsiKeyword.LONG + " " + myRange.getPresentationText(PsiType.LONG);
  }
}
