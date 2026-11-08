// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.types.DfConstantType;

import consulo.language.dataFlow.rangeSet.LongRangeSet;
import consulo.language.dataFlow.value.RelationType;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class DfIntConstantType extends DfConstantType<Integer> implements DfIntType {
  private final @Nullable LongRangeSet myWideRange;

  DfIntConstantType(int value, @Nullable LongRangeSet wideRange) {
    super(value);
    myWideRange = wideRange;
  }

  @Override
  public boolean isSuperType(DfType other) {
    if (other == BOTTOM) return true;
    if (!(other instanceof DfIntConstantType otherConst)) return false;
    if (!otherConst.getValue().equals(getValue())) return false;
    if (myWideRange == null) {
      return otherConst.myWideRange == null || otherConst.myWideRange.equals(getRange());
    }
    return myWideRange.contains(otherConst.getWideRange());
  }

  @Override
  public DfType meet(DfType other) {
    return DfIntType.super.meet(other);
  }

  @Override
  public LongRangeSet getWideRange() {
    return myWideRange == null ? getRange() : myWideRange;
  }

  @Override
  public LongRangeSet getRange() {
    return LongRangeSet.point(getValue());
  }

  @Override
  public DfType fromRelation(RelationType relationType) {
    return DfIntType.super.fromRelation(relationType);
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj || super.equals(obj) && Objects.equals(((DfIntConstantType)obj).myWideRange, myWideRange);
  }
}
