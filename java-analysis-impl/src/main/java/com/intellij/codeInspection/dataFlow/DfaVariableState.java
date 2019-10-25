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

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.value.DfaPsiType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.Contract;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.util.Objects;

class DfaVariableState
{
	@Nonnull
	final DfaFactMap myFactMap;
	private final int myHash;

	DfaVariableState(@Nonnull DfaVariableValue dfaVar)
	{
		this(dfaVar.getInherentFacts());
	}

	public boolean isSuperStateOf(DfaVariableState that)
	{
		return myFactMap.isSuperStateOf(that.myFactMap);
	}

	DfaVariableState(@Nonnull DfaFactMap factMap)
	{
		myFactMap = factMap.with(DfaFactType.SPECIAL_FIELD_VALUE, null);
		myHash = myFactMap.hashCode();
	}

	@Nullable
	DfaVariableState withInstanceofValue(@Nonnull DfaPsiType dfaType)
	{
		if(dfaType.getPsiType() instanceof PsiPrimitiveType)
		{
			return this;
		}
		return withFacts(TypeConstraint.withInstanceOf(myFactMap, dfaType));
	}

	@Nullable
	DfaVariableState withNotInstanceofValue(@Nonnull DfaPsiType dfaType)
	{
		TypeConstraint typeConstraint = getTypeConstraint();
		TypeConstraint newTypeConstraint = typeConstraint.withNotInstanceofValue(dfaType);
		return newTypeConstraint == null ? null : withFact(DfaFactType.TYPE_CONSTRAINT, newTypeConstraint);
	}

	@Nonnull
	DfaVariableState withoutType(@Nonnull DfaPsiType type)
	{
		return withFact(DfaFactType.TYPE_CONSTRAINT, getTypeConstraint().withoutType(type));
	}

	public int hashCode()
	{
		return myHash;
	}

	public boolean equals(Object obj)
	{
		if(obj == this)
		{
			return true;
		}
		if(!(obj instanceof DfaVariableState))
		{
			return false;
		}
		DfaVariableState aState = (DfaVariableState) obj;
		return myHash == aState.myHash && Objects.equals(myFactMap, aState.myFactMap);
	}

	@Nonnull
	protected DfaVariableState createCopy(@Nonnull DfaFactMap factMap)
	{
		return new DfaVariableState(factMap);
	}

	public String toString()
	{
		return "State: " + myFactMap;
	}

	@Nonnull
	Nullability getNullability()
	{
		return DfaNullability.toNullability(myFactMap.get(DfaFactType.NULLABILITY));
	}

	public boolean isNotNull()
	{
		return DfaNullability.isNotNull(myFactMap);
	}

	@Nonnull
	DfaVariableState withNotNull()
	{
		return getNullability() == Nullability.NOT_NULL ? this : withoutFact(DfaFactType.NULLABILITY);
	}

	@Nonnull
	<T> DfaVariableState withFact(DfaFactType<T> type, T value)
	{
		return withFacts(myFactMap.with(type, value));
	}

	@Nonnull
	<T> DfaVariableState withoutFact(DfaFactType<T> type)
	{
		return withFact(type, null);
	}

	@Nullable
	<T> DfaVariableState intersectFact(DfaFactType<T> type, T value)
	{
		return withFacts(myFactMap.intersect(type, value));
	}

	@Nullable
	DfaVariableState intersectMap(DfaFactMap map)
	{
		return withFacts(myFactMap.intersect(map));
	}

	@Contract("null -> null;!null -> !null")
	public DfaVariableState withFacts(@Nullable DfaFactMap facts)
	{
		return facts == null ? null : facts.equals(myFactMap) ? this : createCopy(facts);
	}

	@Nonnull
	public DfaVariableState withValue(DfaValue value)
	{
		return this;
	}

	@Nullable
	public DfaValue getValue()
	{
		return null;
	}

	@Nonnull
	public TypeConstraint getTypeConstraint()
	{
		TypeConstraint fact = getFact(DfaFactType.TYPE_CONSTRAINT);
		return fact == null ? TypeConstraint.empty() : fact;
	}

	@Nullable
	public <T> T getFact(@Nonnull DfaFactType<T> factType)
	{
		return myFactMap.get(factType);
	}
}
