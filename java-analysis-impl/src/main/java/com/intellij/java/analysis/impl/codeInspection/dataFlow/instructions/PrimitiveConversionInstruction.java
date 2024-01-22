// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfLongType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaBinOpValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.rangeClamped;

/**
 * A unary instruction that converts a primitive value from the stack to the desired type
 */
public class PrimitiveConversionInstruction extends EvalInstruction
{
	@jakarta.annotation.Nullable
	private final PsiPrimitiveType myTargetType;

	public PrimitiveConversionInstruction(@jakarta.annotation.Nullable PsiPrimitiveType targetType, @Nullable PsiExpression expression)
	{
		super(expression, 1);
		myTargetType = targetType;
	}

	@Override
	public
	@jakarta.annotation.Nonnull
	DfaValue eval(@jakarta.annotation.Nonnull DfaValueFactory factory,
				  @Nonnull DfaMemoryState state,
				  @Nonnull DfaValue... arguments)
	{
		DfaValue value = arguments[0];
		PsiPrimitiveType type = myTargetType;
		if(value instanceof DfaBinOpValue)
		{
			value = ((DfaBinOpValue) value).tryReduceOnCast(state, type);
		}
		if(value instanceof DfaVariableValue && type != null &&
				(type.equals(value.getType()) ||
						TypeConversionUtil.isSafeConversion(type, value.getType()) &&
								TypeConversionUtil.isSafeConversion(PsiType.INT, type)))
		{
			return value;
		}

		DfType dfType = state.getDfType(value);
		if(dfType instanceof DfConstantType && type != null)
		{
			Object casted = TypeConversionUtil.computeCastTo(((DfConstantType<?>) dfType).getValue(), type);
			return factory.getConstant(casted, type);
		}
		if(TypeConversionUtil.isIntegralNumberType(type))
		{
			LongRangeSet range = DfLongType.extractRange(dfType);
			return factory.fromDfType(rangeClamped(range.castTo(type), PsiType.LONG.equals(type)));
		}
		return factory.getUnknown();
	}

	@Override
	public String toString()
	{
		return "CONVERT_PRIMITIVE";
	}
}
