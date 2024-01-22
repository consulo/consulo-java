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
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Stack;

final class Solver
{
	private final ELattice<Value> lattice;
	private final HashMap<EKey, HashSet<EKey>> dependencies = new HashMap<>();
	private final HashMap<EKey, Pending> pending = new HashMap<>();
	private final HashMap<EKey, Value> solved = new HashMap<>();
	private final Stack<EKey> moving = new Stack<>();

	private final ResultUtil resultUtil;
	private final HashMap<CoreHKey, Equation> equations = new HashMap<>();
	private final Value unstableValue;

	Solver(ELattice<Value> lattice, Value unstableValue)
	{
		this.lattice = lattice;
		this.unstableValue = unstableValue;
		resultUtil = new ResultUtil(lattice);
	}

	Result getUnknownResult()
	{
		return unstableValue;
	}

	void addEquation(Equation equation)
	{
		EKey key = equation.key;
		CoreHKey coreKey = new CoreHKey(key.member, key.dirKey);

		Equation previousEquation = equations.get(coreKey);
		if(previousEquation == null)
		{
			equations.put(coreKey, equation);
		}
		else
		{
			EKey joinKey = new EKey(coreKey.myMethod, coreKey.dirKey, equation.key.stable && previousEquation.key.stable, false);
			Result joinResult = resultUtil.join(equation.result, previousEquation.result);
			Equation joinEquation = new Equation(joinKey, joinResult);
			equations.put(coreKey, joinEquation);
		}
	}

	void queueEquation(Equation equation)
	{
		Result rhs = equation.result;
		if(rhs instanceof Value)
		{
			solved.put(equation.key, (Value) rhs);
			moving.push(equation.key);
		}
		else if(rhs instanceof Pending)
		{
			Pending pendResult = ((Pending) rhs).copy();
			Result norm = normalize(pendResult.delta);
			if(norm instanceof Value)
			{
				solved.put(equation.key, (Value) norm);
				moving.push(equation.key);
			}
			else
			{
				Pending pendResult1 = ((Pending) rhs).copy();
				for(Component component : pendResult1.delta)
				{
					for(EKey trigger : component.ids)
					{
						HashSet<EKey> set = dependencies.get(trigger);
						if(set == null)
						{
							set = new HashSet<>();
							dependencies.put(trigger, set);
						}
						set.add(equation.key);
					}
				}
				pending.put(equation.key, pendResult1);
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

	Map<EKey, Value> solve()
	{
		for(Equation equation : equations.values())
		{
			queueEquation(equation);
		}
		while(!moving.empty())
		{
			EKey id = moving.pop();
			Value value = solved.get(id);

			EKey[] initialPIds = id.stable ? new EKey[]{
					id,
					id.invertStability()
			} : new EKey[]{
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

			EKey[] pIds = new EKey[]{
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
				EKey pId = pIds[i];
				Value pVal = pVals[i];
				HashSet<EKey> dIds = dependencies.get(pId);
				if(dIds == null)
				{
					continue;
				}
				for(EKey dId : dIds)
				{
					Pending pend = pending.remove(dId);
					if(pend != null)
					{
						Result pend1 = substitute(pend, pId, pVal);
						if(pend1 instanceof Value)
						{
							solved.put(dId, (Value) pend1);
							moving.push(dId);
						}
						else
						{
							pending.put(dId, (Pending) pend1);
						}
					}
				}
			}
		}
		pending.clear();
		return solved;
	}

	// substitute id -> value into pending
	Result substitute(@jakarta.annotation.Nonnull Pending pending, @Nonnull EKey id, @jakarta.annotation.Nonnull Value value)
	{
		Component[] sum = pending.delta;
		for(Component intIdComponent : sum)
		{
			if(intIdComponent.remove(id))
			{
				intIdComponent.value = lattice.meet(intIdComponent.value, value);
			}
		}
		return normalize(sum);
	}

	@jakarta.annotation.Nonnull
	Result normalize(@Nonnull Component[] sum)
	{
		Value acc = lattice.bot;
		boolean computableNow = true;
		for(Component prod : sum)
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
		return (acc == lattice.top || computableNow) ? acc : new Pending(sum);
	}
}