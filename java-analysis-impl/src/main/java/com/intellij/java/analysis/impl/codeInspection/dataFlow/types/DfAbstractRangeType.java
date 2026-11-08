// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfIntegralType;

import consulo.language.dataFlow.rangeSet.LongRangeSet;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

abstract class DfAbstractRangeType implements DfIntegralType {
  private final LongRangeSet myRange;
  private final @Nullable LongRangeSet myWideRange;

  DfAbstractRangeType(LongRangeSet range, @Nullable LongRangeSet wideRange) {
    myRange = range;
    myWideRange = wideRange;
  }

  @Override
  public LongRangeSet getRange() {
    return myRange;
  }

  @Override
  public LongRangeSet getWideRange() {
    return myWideRange == null ? myRange : myWideRange;
  }

  @Override
  public int hashCode() {
    return myRange.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this ||
           obj != null && obj.getClass() == getClass() && 
           ((DfAbstractRangeType)obj).myRange.equals(myRange) &&
           Objects.equals(((DfAbstractRangeType)obj).myWideRange, myWideRange);
  }

  @Override
  public abstract String toString();
}
