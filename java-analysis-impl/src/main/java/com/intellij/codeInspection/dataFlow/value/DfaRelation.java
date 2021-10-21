/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.types.DfConstantType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

/**
 * A condition that represents a relation between two DfaValues
 */
public final class DfaRelation extends DfaCondition
{
	@Nonnull
	@Override
	public DfaRelation negate()
	{
		return new DfaRelation(myLeftOperand, myRightOperand, myRelation.getNegated());
	}

	private
	@Nonnull
	final DfaValue myLeftOperand;
	private
	@Nonnull
	final DfaValue myRightOperand;
	private
	@Nonnull
	final RelationType myRelation;

	private DfaRelation(@Nonnull DfaValue leftOperand, @Nonnull DfaValue rightOperand, @Nonnull RelationType relationType)
	{
		myLeftOperand = leftOperand;
		myRightOperand = rightOperand;
		myRelation = relationType;
	}

	@Nonnull
	public DfaValue getLeftOperand()
	{
		return myLeftOperand;
	}

	@Nonnull
	public DfaValue getRightOperand()
	{
		return myRightOperand;
	}

	public static DfaRelation createRelation(@Nonnull DfaValue dfaLeft, @Nonnull RelationType relationType, @Nonnull DfaValue dfaRight)
	{
		if((relationType == RelationType.IS || relationType == RelationType.IS_NOT) &&
				dfaRight instanceof DfaTypeValue && !(dfaLeft instanceof DfaTypeValue))
		{
			return new DfaRelation(dfaLeft, dfaRight, relationType);
		}
		if(dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaBoxedValue || dfaLeft instanceof DfaBinOpValue
				|| dfaRight instanceof DfaVariableValue || dfaRight instanceof DfaBoxedValue || dfaRight instanceof DfaBinOpValue)
		{
			if(!(dfaLeft instanceof DfaVariableValue || dfaLeft instanceof DfaBoxedValue || dfaLeft instanceof DfaBinOpValue) ||
					(dfaRight instanceof DfaBinOpValue && !(dfaLeft instanceof DfaBinOpValue)))
			{
				RelationType flipped = relationType.getFlipped();
				return flipped == null ? null : new DfaRelation(dfaRight, dfaLeft, flipped);
			}
			return new DfaRelation(dfaLeft, dfaRight, relationType);
		}
		if(dfaLeft instanceof DfaTypeValue && dfaRight.getDfType() instanceof DfConstantType)
		{
			return createConstBasedRelation((DfaTypeValue) dfaLeft, relationType, dfaRight);
		}
		else if(dfaRight instanceof DfaTypeValue && dfaLeft.getDfType() instanceof DfConstantType)
		{
			return createConstBasedRelation((DfaTypeValue) dfaRight, relationType, dfaLeft);
		}
		return null;
	}

	@Nonnull
	private static DfaRelation createConstBasedRelation(DfaTypeValue dfaLeft, RelationType relationType, DfaValue dfaRight)
	{
		if(dfaRight.getDfType() == DfTypes.NULL && DfaNullability.fromDfType(dfaLeft.getDfType()) == DfaNullability.NULLABLE)
		{
			return new DfaRelation(dfaLeft.getFactory().fromDfType(DfaNullability.NULLABLE.asDfType()), dfaRight, relationType);
		}
		return new DfaRelation(dfaLeft.getFactory().getUnknown(), dfaRight, relationType);
	}

	public boolean isEquality()
	{
		return myRelation == RelationType.EQ;
	}

	public boolean isNonEquality()
	{
		return myRelation == RelationType.NE || myRelation == RelationType.GT || myRelation == RelationType.LT;
	}

	@Nonnull
	public RelationType getRelation()
	{
		return myRelation;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}
		DfaRelation relation = (DfaRelation) o;
		return myLeftOperand == relation.myLeftOperand &&
				myRightOperand == relation.myRightOperand &&
				myRelation == relation.myRelation;
	}

	@Override
	public int hashCode()
	{
		int result = 31 + myLeftOperand.hashCode();
		result = 31 * result + myRightOperand.hashCode();
		result = 31 * result + myRelation.hashCode();
		return result;
	}

	@NonNls
	public String toString()
	{
		return myLeftOperand + " " + myRelation + " " + myRightOperand;
	}
}
