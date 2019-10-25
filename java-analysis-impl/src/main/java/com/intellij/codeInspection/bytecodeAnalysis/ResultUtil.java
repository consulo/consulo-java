/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.util.ArrayUtil;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

class ResultUtil
{
	private static final EKey[] EMPTY_PRODUCT = new EKey[0];
	private final ELattice<Value> lattice;
	final Value top;
	final Value bottom;

	ResultUtil(ELattice<Value> lattice)
	{
		this.lattice = lattice;
		top = lattice.top;
		bottom = lattice.bot;
	}

	Result join(Result r1, Result r2)
	{
		Result result = checkFinal(r1, r2);
		if(result != null)
		{
			return result;
		}
		result = checkFinal(r1, r2);
		if(result != null)
		{
			return result;
		}
		if(r1 instanceof Value && r2 instanceof Value)
		{
			return lattice.join((Value) r1, (Value) r2);
		}
		if(r1 instanceof Value && r2 instanceof Pending)
		{
			return addSingle((Pending) r2, (Value) r1);
		}
		if(r1 instanceof Pending && r2 instanceof Value)
		{
			return addSingle((Pending) r1, (Value) r2);
		}
		assert r1 instanceof Pending && r2 instanceof Pending;
		Pending pending1 = (Pending) r1;
		Pending pending2 = (Pending) r2;
		Set<Component> sum = new HashSet<>();
		sum.addAll(Arrays.asList(pending1.delta));
		sum.addAll(Arrays.asList(pending2.delta));
		return new Pending(sum);
	}

	@Nullable
	private Result checkFinal(Result r1, Result r2)
	{
		if(r1 == top)
		{
			return r1;
		}
		if(r1 == bottom)
		{
			return r2;
		}
		return null;
	}

	@Nonnull
	private Result addSingle(Pending pending, Value value)
	{
		for(int i = 0; i < pending.delta.length; i++)
		{
			Component component = pending.delta[i];
			if(component.ids.length == 0)
			{
				Value join = lattice.join(component.value, value);
				if(join == top)
				{
					return top;
				}
				else if(join == component.value)
				{
					return pending;
				}
				else
				{
					Component[] components = pending.delta.clone();
					components[i] = new Component(join, EMPTY_PRODUCT);
					return new Pending(components);
				}
			}
		}
		return new Pending(ArrayUtil.append(pending.delta, new Component(value, EMPTY_PRODUCT)));
	}
}
