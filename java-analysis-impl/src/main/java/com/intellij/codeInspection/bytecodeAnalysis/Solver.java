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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Stack;

import org.jetbrains.annotations.NotNull;

final class Solver
{
	private final ELattice<Value> lattice;
	private final HashMap<HKey, HashSet<HKey>> dependencies = new HashMap<HKey, HashSet<HKey>>();
	private final HashMap<HKey, HPending> pending = new HashMap<HKey, HPending>();
	private final HashMap<HKey, Value> solved = new HashMap<HKey, Value>();
	private final Stack<HKey> moving = new Stack<HKey>();

	private final HResultUtil resultUtil;
	private final HashMap<CoreHKey, HEquation> equations = new HashMap<CoreHKey, HEquation>();
	private final Value unstableValue;

	Solver(ELattice<Value> lattice, Value unstableValue)
	{
		this.lattice = lattice;
		this.unstableValue = unstableValue;
		resultUtil = new HResultUtil(lattice);
	}

	void addEquation(HEquation equation)
	{
		HKey key = equation.key;
		CoreHKey coreKey = new CoreHKey(key.key, key.dirKey);

		HEquation previousEquation = equations.get(coreKey);
		if(previousEquation == null)
		{
			equations.put(coreKey, equation);
		}
		else
		{
			HKey joinKey = new HKey(coreKey.key, coreKey.dirKey, equation.key.stable && previousEquation.key.stable, true);
			HResult joinResult = resultUtil.join(equation.result, previousEquation.result);
			HEquation joinEquation = new HEquation(joinKey, joinResult);
			equations.put(coreKey, joinEquation);
		}
	}

	void queueEquation(HEquation equation)
	{
		HResult rhs = equation.result;
		if(rhs instanceof HFinal)
		{
			solved.put(equation.key, ((HFinal) rhs).value);
			moving.push(equation.key);
		}
		else if(rhs instanceof HPending)
		{
			HPending pendResult = ((HPending) rhs).copy();
			HResult norm = normalize(pendResult.delta);
			if(norm instanceof HFinal)
			{
				solved.put(equation.key, ((HFinal) norm).value);
				moving.push(equation.key);
			}
			else
			{
				HPending pendResult1 = ((HPending) rhs).copy();
				for(HComponent component : pendResult1.delta)
				{
					for(HKey trigger : component.ids)
					{
						HashSet<HKey> set = dependencies.get(trigger);
						if(set == null)
						{
							set = new HashSet<HKey>();
							dependencies.put(trigger, set);
						}
						set.add(equation.key);
					}
					pending.put(equation.key, pendResult1);
				}
			}
		}
	}

	Value negate(Value value)
	{
		switch(value)
		{
			case True:
				return Value.False;
			case False:
				return Value.True;
			default:
				return value;
		}
	}

	Map<HKey, Value> solve()
	{
		for(HEquation hEquation : equations.values())
		{
			queueEquation(hEquation);
		}
		while(!moving.empty())
		{
			HKey id = moving.pop();
			Value value = solved.get(id);

			HKey[] initialPIds = id.stable ? new HKey[]{
					id,
					id.invertStability()
			} : new HKey[]{
					id.invertStability(),
					id
			};
			Value[] initialPVals = id.stable ? new Value[]{
					value,
					value
			} : new Value[]{
					value,
					unstableValue
			};

			HKey[] pIds = new HKey[]{
					initialPIds[0],
					initialPIds[1],
					initialPIds[0].negate(),
					initialPIds[1].negate()
			};
			Value[] pVals = new Value[]{
					initialPVals[0],
					initialPVals[1],
					negate(initialPVals[0]),
					negate(initialPVals[1])
			};

			for(int i = 0; i < pIds.length; i++)
			{
				HKey pId = pIds[i];
				Value pVal = pVals[i];
				HashSet<HKey> dIds = dependencies.get(pId);
				if(dIds == null)
				{
					continue;
				}
				for(HKey dId : dIds)
				{
					HPending pend = pending.remove(dId);
					if(pend != null)
					{
						HResult pend1 = substitute(pend, pId, pVal);
						if(pend1 instanceof HFinal)
						{
							HFinal fi = (HFinal) pend1;
							solved.put(dId, fi.value);
							moving.push(dId);
						}
						else
						{
							pending.put(dId, (HPending) pend1);
						}
					}
				}
			}
		}
		pending.clear();
		return solved;
	}

	// substitute id -> value into pending
	HResult substitute(@NotNull HPending pending, @NotNull HKey id, @NotNull Value value)
	{
		HComponent[] sum = pending.delta;
		for(HComponent intIdComponent : sum)
		{
			if(intIdComponent.remove(id))
			{
				intIdComponent.value = lattice.meet(intIdComponent.value, value);
			}
		}
		return normalize(sum);
	}

	@NotNull
	HResult normalize(@NotNull HComponent[] sum)
	{
		Value acc = lattice.bot;
		boolean computableNow = true;
		for(HComponent prod : sum)
		{
			if(prod.isEmpty() || prod.value == lattice.bot)
			{
				acc = lattice.join(acc, prod.value);
			}
			else
			{
				computableNow = false;
			}
		}
		return (acc == lattice.top || computableNow) ? new HFinal(acc) : new HPending(sum);
	}
}
