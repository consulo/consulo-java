// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.types;

import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.types.DfIntegralType;

import consulo.language.dataFlow.rangeSet.LongRangeBinOp;
import consulo.language.dataFlow.rangeSet.LongRangeSet;
import consulo.language.dataFlow.rangeSet.LongRangeType;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiTypes;

public interface DfLongType extends DfJvmIntegralType {
  @Override
  LongRangeSet getRange();

  @Override
  default DfType eval(DfType other, LongRangeBinOp op) {
    if (!(other instanceof DfLongType || other instanceof DfIntType && op.isShift())) return DfTypes.LONG;
    LongRangeSet result = op.eval(getRange(), ((DfIntegralType)other).getRange(), getLongRangeType());
    LongRangeSet wideResult = op.evalWide(getWideRange(), ((DfIntegralType)other).getWideRange(), getLongRangeType());
    return DfTypes.longRange(result, wideResult);
  }

  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiTypes.longType();
  }

  static LongRangeSet extractRange(DfType type) {
    return type instanceof DfIntegralType ? ((DfIntegralType)type).getRange() : LongRangeSet.all();
  }

  @Override
  default LongRangeType getLongRangeType() {
    return LongRangeType.INT64;
  }
}
