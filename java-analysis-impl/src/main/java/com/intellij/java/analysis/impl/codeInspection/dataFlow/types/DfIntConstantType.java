// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.language.psi.PsiPrimitiveType;
import jakarta.annotation.Nonnull;

class DfIntConstantType extends DfConstantType<Integer> implements DfIntType {
  DfIntConstantType(int value) {
    super(value);
  }

  @Nonnull
  @Override
  public PsiPrimitiveType getPsiType() {
    return DfIntType.super.getPsiType();
  }

  @jakarta.annotation.Nonnull
  @Override
  public LongRangeSet getRange() {
    return LongRangeSet.point(getValue());
  }
}
