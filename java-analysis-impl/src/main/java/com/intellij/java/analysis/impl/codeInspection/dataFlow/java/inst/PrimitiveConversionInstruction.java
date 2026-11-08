// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import consulo.language.dataFlow.ir.DfaAnchor;
import consulo.language.dataFlow.ir.EvalInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.rangeSet.LongRangeBinOp;
import consulo.language.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfIntType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfLongType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfPrimitiveType;
import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.value.DfaBinOpValue;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import org.jspecify.annotations.Nullable;

/**
 * A unary instruction that converts a primitive value from the stack to the desired type
 */
public class PrimitiveConversionInstruction extends EvalInstruction {
  private final PsiPrimitiveType myTargetType;

  public PrimitiveConversionInstruction(PsiPrimitiveType targetType, @Nullable DfaAnchor anchor) {
    super(anchor, 1);
    myTargetType = targetType;
  }

  @Override
  public DfaValue eval(DfaValueFactory factory,
                                DfaMemoryState state,
                                DfaValue ... arguments) {
    DfaValue value = arguments[0];
    if (value instanceof DfaBinOpValue) {
      value = tryReduceOnCast(((DfaBinOpValue)value), state, myTargetType);
    }

    DfType dfType = state.getDfType(value);
    if (value instanceof DfaVariableValue && dfType instanceof DfIntType) {
      LongRangeSet set = JvmPsiRangeSetUtil.typeRange(myTargetType);
      if (set != null && !LongRangeSet.all().equals(set) && ((DfIntType)dfType).meetRange(set).equals(dfType)) {
        return value;
      }
    }
    if (dfType instanceof DfPrimitiveType) {
      return factory.fromDfType(((DfPrimitiveType)dfType).castTo(myTargetType));
    }
    return factory.getUnknown();
  }

  @Override
  public String toString() {
    return "CONVERT_PRIMITIVE " + myTargetType.getPresentableText();
  }

  private static DfaValue tryReduceOnCast(DfaBinOpValue value,
                                                   DfaMemoryState state,
                                                   PsiPrimitiveType type) {
    if (!TypeConversionUtil.isIntegralNumberType(type)) return value;
    LongRangeBinOp operation = value.getOperation();
    if ((operation == LongRangeBinOp.PLUS || operation == LongRangeBinOp.MINUS) &&
        JvmPsiRangeSetUtil.castTo(DfLongType.extractRange(state.getDfType(value.getRight())), type).equals(LongRangeSet.point(0))) {
      return value.getLeft();
    }
    if (operation == LongRangeBinOp.PLUS &&
        JvmPsiRangeSetUtil.castTo(DfLongType.extractRange(state.getDfType(value.getLeft())), type).equals(LongRangeSet.point(0))) {
      return value.getRight();
    }
    return value;
  }
}
