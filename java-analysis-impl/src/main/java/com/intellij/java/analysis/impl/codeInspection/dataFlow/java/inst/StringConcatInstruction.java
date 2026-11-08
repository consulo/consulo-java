// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.CustomMethodHandlers;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.SpecialField;
import consulo.language.dataFlow.ir.DfaAnchor;
import consulo.language.dataFlow.ir.EvalInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.rangeSet.LongRangeBinOp;
import consulo.language.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfBooleanType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfFloatingPointType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfIntType;
import consulo.language.dataFlow.types.DfIntegralType;
import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import org.jspecify.annotations.Nullable;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.NULL;
import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.concatenationResult;
import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.intRange;
import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.intValue;
import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.referenceConstant;

public class StringConcatInstruction extends EvalInstruction {
  private final TypeConstraint myStringType;

  public StringConcatInstruction(@Nullable DfaAnchor anchor, TypeConstraint stringType) {
    super(anchor, 2);
    myStringType = stringType;
  }

  @Override
  public DfaValue eval(DfaValueFactory factory,
                                DfaMemoryState state,
                                DfaValue ... arguments) {
    DfaValue left = arguments[0];
    DfaValue right = arguments[1];
    String leftString = getString(state, left);
    String rightString = getString(state, right);
    if (leftString != null && rightString != null &&
        leftString.length() + rightString.length() <= CustomMethodHandlers.MAX_STRING_CONSTANT_LENGTH_TO_TRACK) {
      return factory.fromDfType(concatenationResult(leftString + rightString, myStringType));
    }
    DfIntType leftRange = getLength(factory, state, left, leftString);
    DfIntType rightRange = getLength(factory, state, right, rightString);
    DfType resultRange = leftRange.eval(rightRange, LongRangeBinOp.PLUS);
    DfType result = resultRange.isConst(0)
                    ? referenceConstant("", myStringType)
                    : SpecialField.STRING_LENGTH.asDfType(resultRange).meet(myStringType.asDfType());
    return factory.fromDfType(result);
  }

  private static DfIntType getLength(DfaValueFactory factory, DfaMemoryState state, DfaValue dfaValue, @Nullable String constValue) {
    if (constValue != null) {
      return intValue(constValue.length());
    }
    DfType lengthType;
    if (dfaValue.getDfType() instanceof DfIntegralType) {
      DfType decimalString = CustomMethodHandlers.numberAsDecimalString(state, dfaValue);
      lengthType = SpecialField.STRING_LENGTH.getFromQualifier(decimalString);
    } else if (dfaValue.getDfType() instanceof DfBooleanType) {
      lengthType = intRange(LongRangeSet.range(4, 5)); // "true" or "false"
    } else if (dfaValue.getDfType() instanceof DfFloatingPointType) {
      lengthType = intRange(LongRangeSet.range(3, 26));
    } else {
      DfaValue lengthValue = SpecialField.STRING_LENGTH.createValue(factory, dfaValue);
      lengthType = state.getDfType(lengthValue);
    }
    if (lengthType instanceof DfIntType intType) return intType;
    return (DfIntType)intRange(LongRangeSet.range(0, Integer.MAX_VALUE));
  }
  
  private static @Nullable String getString(DfaMemoryState state, DfaValue value) {
    DfType dfType = state.getDfType(value);
    if (dfType.equals(NULL)) {
      return "null";
    }
    Object constant = dfType.getConstantOfType(Object.class);
    // Do not process float/double constants, as their string representation may depend on JDK version
    return constant instanceof String || constant instanceof Integer || constant instanceof Long || 
           constant instanceof Boolean ? constant.toString() : null;
  }

  @Override
  public String toString() {
    return "STRING_CONCAT";
  }
}
