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

package com.intellij.codeInspection.dataFlow.value;

import java.util.ArrayList;
import java.util.Map;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.util.containers.ContainerUtil;

public class DfaTypeValue extends DfaValue
{
	public static class Factory
	{
		private final Map<DfaPsiType, ArrayList<DfaTypeValue>> myCache = ContainerUtil.newHashMap();
		@Nonnull
		private final DfaValueFactory myFactory;

		Factory(@Nonnull DfaValueFactory factory)
		{
			myFactory = factory;
		}

		@Nonnull
		DfaTypeValue createTypeValue(@Nonnull DfaPsiType type, @Nonnull Nullness nullness)
		{
			ArrayList<DfaTypeValue> conditions = myCache.get(type);
			if(conditions == null)
			{
				conditions = new ArrayList<>();
				myCache.put(type, conditions);
			}
			else
			{
				for(DfaTypeValue aType : conditions)
				{
					if(aType.myNullness == nullness)
					{
						return aType;
					}
				}
			}

			DfaTypeValue result = new DfaTypeValue(type, nullness, myFactory);
			conditions.add(result);
			return result;
		}

	}

	@Nonnull
	private final DfaPsiType myType;
	@Nonnull
	private final Nullness myNullness;

	private DfaTypeValue(@Nonnull DfaPsiType type, @Nonnull Nullness nullness, @Nonnull DfaValueFactory factory)
	{
		super(factory);
		myType = type;
		myNullness = nullness;
	}

	@Nonnull
	public DfaPsiType getDfaType()
	{
		return myType;
	}

	public boolean isNullable()
	{
		return myNullness == Nullness.NULLABLE;
	}

	public boolean isNotNull()
	{
		return myNullness == Nullness.NOT_NULL;
	}

	@Nonnull
	public Nullness getNullness()
	{
		return myNullness;
	}

	public DfaTypeValue withNullness(Nullness nullness)
	{
		return nullness == myNullness ? this : myFactory.getTypeFactory().createTypeValue(myType, nullness);
	}

	@NonNls
	public String toString()
	{
		return myType + ", nullable=" + myNullness;
	}

}
