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

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class PuritySolver
{
	private final HashMap<EKey, Effects> solved = new HashMap<>();
	private final HashMap<EKey, Set<EKey>> dependencies = new HashMap<>();
	private final ArrayDeque<EKey> moving = new ArrayDeque<>();
	HashMap<EKey, Effects> pending = new HashMap<>();

	void addEquation(EKey key, Effects effects)
	{
		Set<EKey> depKeys = effects.dependencies().collect(Collectors.toSet());
		if(depKeys.isEmpty())
		{
			solved.put(key, effects);
			moving.add(key);
		}
		else
		{
			pending.put(key, effects);
			for(EKey depKey : depKeys)
			{
				dependencies.computeIfAbsent(depKey, k -> new HashSet<>()).add(key);
			}
		}
	}

	public Map<EKey, Effects> solve()
	{
		while(!moving.isEmpty())
		{
			EKey key = moving.pop();
			Effects effects = solved.get(key);

			EKey[] propagateKeys;
			Effects[] propagateEffects;

			if(key.stable)
			{
				propagateKeys = new EKey[]{
						key,
						key.mkUnstable()
				};
				propagateEffects = new Effects[]{
						effects,
						effects
				};
			}
			else
			{
				propagateKeys = new EKey[]{
						key.mkStable(),
						key
				};
				propagateEffects = new Effects[]{
						effects,
						new Effects(DataValue.UnknownDataValue1, Effects.TOP_EFFECTS)
				};
			}
			for(int i = 0; i < propagateKeys.length; i++)
			{
				EKey pKey = propagateKeys[i];
				Effects pEffects = propagateEffects[i];
				Set<EKey> dKeys = dependencies.remove(pKey);
				if(dKeys != null)
				{
					for(EKey dKey : dKeys)
					{
						Effects dEffects = pending.remove(dKey);
						if(dEffects == null)
						{
							// already solved, for example, solution is top
							continue;
						}
						Set<EffectQuantum> newEffects = new HashSet<>();
						Set<EffectQuantum> delta = null;
						DataValue returnValue = substitute(dEffects.returnValue, pKey, pEffects);

						for(EffectQuantum dEffect : dEffects.effects)
						{
							if(dEffect instanceof EffectQuantum.CallQuantum)
							{
								EffectQuantum.CallQuantum call = substitute((EffectQuantum.CallQuantum) dEffect, pKey, pEffects);
								if(call.key.equals(pKey))
								{
									delta = substitute(pEffects, call.data, call.isStatic);
									if(delta.equals(Effects.TOP_EFFECTS))
									{
										newEffects = delta;
										break;
									}
									newEffects.addAll(delta);
								}
								else
								{
									newEffects.add(call);
								}
								continue;
							}
							if(dEffect instanceof EffectQuantum.ReturnChangeQuantum)
							{
								EffectQuantum.ReturnChangeQuantum retChange = (EffectQuantum.ReturnChangeQuantum) dEffect;
								if(retChange.key.equals(pKey))
								{
									if(pEffects.returnValue != DataValue.LocalDataValue)
									{
										newEffects = delta = Effects.TOP_EFFECTS;
										break;
									}
									continue;
								}
							}
							if(dEffect instanceof EffectQuantum.FieldReadQuantum && ((EffectQuantum.FieldReadQuantum) dEffect).key.equals(pKey))
							{
								newEffects.addAll(pEffects.effects);
								continue;
							}
							newEffects.add(dEffect);
						}

						if(Effects.TOP_EFFECTS.equals(delta) && returnValue.equals(DataValue.UnknownDataValue1))
						{
							solved.put(dKey, new Effects(returnValue, Effects.TOP_EFFECTS));
							moving.push(dKey);
						}
						else
						{
							Effects result = new Effects(returnValue, newEffects);
							if(result.dependencies().findFirst().isPresent())
							{
								pending.put(dKey, result);
							}
							else
							{
								solved.put(dKey, result);
								moving.push(dKey);
							}
						}
					}
				}
			}
		}
		return solved;
	}

	public void addPlainFieldEquations(Predicate<MemberDescriptor> plainByDefault)
	{
		for(EKey key : dependencies.keySet())
		{
			if(key.getDirection() == Direction.Volatile && plainByDefault.test(key.member))
			{
				// Absent fields are considered non-volatile
				solved.putIfAbsent(key, new Effects(DataValue.UnknownDataValue1, Collections.emptySet()));
				moving.add(key);
			}
		}
	}

	private static EffectQuantum.CallQuantum substitute(EffectQuantum.CallQuantum call, EKey pKey, Effects pEffects)
	{
		List<DataValue> list = new ArrayList<>();
		boolean same = true;
		for(DataValue value : call.data)
		{
			DataValue newValue = substitute(value, pKey, pEffects);
			same &= newValue.equals(value);
			list.add(newValue);
		}
		return same ? call : new EffectQuantum.CallQuantum(call.key, list.toArray(DataValue.EMPTY), call.isStatic);
	}

	private static DataValue substitute(DataValue value, EKey key, Effects effects)
	{
		if(value instanceof DataValue.ReturnDataValue && ((DataValue.ReturnDataValue) value).key.equals(key))
		{
			return effects.returnValue == DataValue.LocalDataValue ? DataValue.LocalDataValue : DataValue.UnknownDataValue1;
		}
		return value;
	}

	private static Set<EffectQuantum> substitute(Effects effects, DataValue[] data, boolean isStatic)
	{
		if(effects.effects.isEmpty() || Effects.TOP_EFFECTS.equals(effects.effects))
		{
			return effects.effects;
		}
		Set<EffectQuantum> newEffects = new HashSet<>(effects.effects.size());
		int shift = isStatic ? 0 : 1;
		for(EffectQuantum effect : effects.effects)
		{
			DataValue arg = null;
			if(effect == EffectQuantum.ThisChangeQuantum)
			{
				arg = data[0];
			}
			else if(effect instanceof EffectQuantum.ParamChangeQuantum)
			{
				EffectQuantum.ParamChangeQuantum paramChange = ((EffectQuantum.ParamChangeQuantum) effect);
				arg = data[paramChange.n + shift];
			}
			if(arg == null || arg == DataValue.LocalDataValue)
			{
				continue;
			}
			if(arg == DataValue.ThisDataValue || arg == DataValue.OwnedDataValue)
			{
				newEffects.add(EffectQuantum.ThisChangeQuantum);
				continue;
			}
			if(arg instanceof DataValue.ParameterDataValue)
			{
				newEffects.add(new EffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue) arg).n));
				continue;
			}
			if(arg instanceof DataValue.ReturnDataValue)
			{
				newEffects.add(new EffectQuantum.ReturnChangeQuantum(((DataValue.ReturnDataValue) arg).key));
				continue;
			}
			return Effects.TOP_EFFECTS;
		}
		return newEffects;
	}
}