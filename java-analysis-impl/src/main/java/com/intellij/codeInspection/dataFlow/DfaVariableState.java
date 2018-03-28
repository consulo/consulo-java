/*
 * Copyright 2013-2017 consulo.io
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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import com.intellij.codeInspection.dataFlow.value.DfaPsiType;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.util.containers.ContainerUtil;

class DfaVariableState
{
	@Nonnull
	final Set<DfaPsiType> myInstanceofValues;
	@Nonnull
	final Set<DfaPsiType> myNotInstanceofValues;
	@Nonnull
	final DfaFactMap myFactMap;
	private final int myHash;

	DfaVariableState(@Nonnull DfaVariableValue dfaVar)
	{
		this(Collections.emptySet(), Collections.emptySet(), dfaVar.getInherentFacts());
	}

	public boolean isSuperStateOf(DfaVariableState that)
	{
		if(!myNotInstanceofValues.equals(that.myNotInstanceofValues))
		{
			return false;
		}
		if(!myInstanceofValues.equals(that.myNotInstanceofValues))
		{
			return false;
		}
		return myFactMap.isSuperStateOf(that.myFactMap);
	}

	DfaVariableState(@Nonnull Set<DfaPsiType> instanceofValues, @Nonnull Set<DfaPsiType> notInstanceofValues, @Nonnull DfaFactMap factMap)
	{
		myInstanceofValues = instanceofValues;
		myNotInstanceofValues = notInstanceofValues;
		myFactMap = factMap;
		myHash = Objects.hash(myInstanceofValues, myNotInstanceofValues, myFactMap);
	}

	private boolean checkInstanceofValue(@Nonnull DfaPsiType dfaType)
	{
		if(myInstanceofValues.contains(dfaType))
		{
			return true;
		}

		for(DfaPsiType dfaTypeValue : myNotInstanceofValues)
		{
			if(dfaTypeValue.isAssignableFrom(dfaType))
			{
				return false;
			}
		}

		for(DfaPsiType dfaTypeValue : myInstanceofValues)
		{
			if(!dfaType.isConvertibleFrom(dfaTypeValue))
			{
				return false;
			}
		}

		return true;
	}

	@Nullable
	DfaVariableState withInstanceofValue(@Nonnull DfaTypeValue dfaType)
	{
		if(dfaType.getDfaType().getPsiType() instanceof PsiPrimitiveType)
		{
			return this;
		}

		if(checkInstanceofValue(dfaType.getDfaType()))
		{
			DfaVariableState result = dfaType.isNullable() ? withFact(DfaFactType.CAN_BE_NULL, true) : this;
			List<DfaPsiType> moreGeneric = ContainerUtil.newArrayList();
			for(DfaPsiType alreadyInstanceof : myInstanceofValues)
			{
				if(dfaType.getDfaType().isAssignableFrom(alreadyInstanceof))
				{
					return result;
				}
				if(alreadyInstanceof.isAssignableFrom(dfaType.getDfaType()))
				{
					moreGeneric.add(alreadyInstanceof);
				}
			}

			HashSet<DfaPsiType> newInstanceof = ContainerUtil.newHashSet(myInstanceofValues);
			newInstanceof.removeAll(moreGeneric);
			newInstanceof.add(dfaType.getDfaType());
			result = createCopy(newInstanceof, myNotInstanceofValues, result.myFactMap);
			return result;
		}

		return null;
	}

	@javax.annotation.Nullable
	DfaVariableState withNotInstanceofValue(@Nonnull DfaTypeValue dfaType)
	{
		if(myNotInstanceofValues.contains(dfaType.getDfaType()))
		{
			return this;
		}

		for(DfaPsiType dfaTypeValue : myInstanceofValues)
		{
			if(dfaType.getDfaType().isAssignableFrom(dfaTypeValue))
			{
				return null;
			}
		}

		List<DfaPsiType> moreSpecific = ContainerUtil.newArrayList();
		for(DfaPsiType alreadyNotInstanceof : myNotInstanceofValues)
		{
			if(alreadyNotInstanceof.isAssignableFrom(dfaType.getDfaType()))
			{
				return this;
			}
			if(dfaType.getDfaType().isAssignableFrom(alreadyNotInstanceof))
			{
				moreSpecific.add(alreadyNotInstanceof);
			}
		}

		HashSet<DfaPsiType> newNotInstanceof = ContainerUtil.newHashSet(myNotInstanceofValues);
		newNotInstanceof.removeAll(moreSpecific);
		newNotInstanceof.add(dfaType.getDfaType());
		return createCopy(myInstanceofValues, newNotInstanceof, myFactMap);
	}

	@Nonnull
	DfaVariableState withoutType(@Nonnull DfaPsiType type)
	{
		if(myInstanceofValues.contains(type))
		{
			HashSet<DfaPsiType> newInstanceof = ContainerUtil.newHashSet(myInstanceofValues);
			newInstanceof.remove(type);
			return createCopy(newInstanceof, myNotInstanceofValues, myFactMap);
		}
		if(myNotInstanceofValues.contains(type))
		{
			HashSet<DfaPsiType> newNotInstanceof = ContainerUtil.newHashSet(myNotInstanceofValues);
			newNotInstanceof.remove(type);
			return createCopy(myInstanceofValues, newNotInstanceof, myFactMap);
		}
		return this;
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
		return myHash == aState.myHash && myInstanceofValues.equals(aState.myInstanceofValues) && myNotInstanceofValues.equals(aState.myNotInstanceofValues) && Objects.equals(myFactMap, aState
				.myFactMap);
	}

	@Nonnull
	protected DfaVariableState createCopy(@Nonnull Set<DfaPsiType> instanceofValues, @Nonnull Set<DfaPsiType> notInstanceofValues, @Nonnull DfaFactMap factMap)
	{
		return new DfaVariableState(instanceofValues, notInstanceofValues, factMap);
	}

	public String toString()
	{
		@NonNls StringBuilder buf = new StringBuilder("State:");

		if(!myInstanceofValues.isEmpty())
		{
			buf.append(" instanceof ").append(StringUtil.join(myInstanceofValues, ","));
		}

		if(!myNotInstanceofValues.isEmpty())
		{
			buf.append(" not instanceof ").append(StringUtil.join(myNotInstanceofValues, ","));
		}

		String factString = myFactMap.toString();
		if(!factString.isEmpty())
		{
			buf.append(" ").append(factString);
		}
		return buf.toString();
	}

	@Nonnull
	Nullness getNullability()
	{
		return NullnessUtil.fromBoolean(myFactMap.get(DfaFactType.CAN_BE_NULL));
	}

	public boolean isNotNull()
	{
		return getNullability() == Nullness.NOT_NULL;
	}

	@Nonnull
	DfaVariableState withNotNull()
	{
		return getNullability() == Nullness.NOT_NULL ? this : withoutFact(DfaFactType.CAN_BE_NULL);
	}

	@Nonnull
	<T> DfaVariableState withFact(DfaFactType<T> type, T value)
	{
		DfaFactMap factMap = myFactMap.with(type, value);
		return myFactMap.equals(factMap) ? this : createCopy(myInstanceofValues, myNotInstanceofValues, factMap);
	}

	<T> DfaVariableState withoutFact(DfaFactType<T> type)
	{
		return withFact(type, null);
	}

	@Nullable
	<T> DfaVariableState intersectFact(DfaFactType<T> type, T value)
	{
		DfaFactMap factMap = myFactMap.intersect(type, value);
		return factMap == null ? null : myFactMap.equals(factMap) ? this : createCopy(myInstanceofValues, myNotInstanceofValues, factMap);
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
	public Set<DfaPsiType> getInstanceofValues()
	{
		return myInstanceofValues;
	}

	@Nonnull
	public Set<DfaPsiType> getNotInstanceofValues()
	{
		return myNotInstanceofValues;
	}

	@javax.annotation.Nullable
	public <T> T getFact(@Nonnull DfaFactType<T> factType)
	{
		return myFactMap.get(factType);
	}
}
