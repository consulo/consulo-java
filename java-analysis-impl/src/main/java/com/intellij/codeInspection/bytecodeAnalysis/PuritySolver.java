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
import java.util.Set;
import java.util.Stack;

final class PuritySolver {
  private HashMap<HKey, Set<HEffectQuantum>> solved = new HashMap<HKey, Set<HEffectQuantum>>();
  private HashMap<HKey, Set<HKey>> dependencies = new HashMap<HKey, Set<HKey>>();
  private final Stack<HKey> moving = new Stack<HKey>();
  private HashMap<HKey, Set<HEffectQuantum>> pending = new HashMap<HKey, Set<HEffectQuantum>>();

  void addEquation(HKey key, Set<HEffectQuantum> effects) {
    Set<HKey> callKeys = new HashSet<HKey>();
    for (HEffectQuantum effect : effects) {
      if (effect instanceof HEffectQuantum.CallQuantum) {
        callKeys.add(((HEffectQuantum.CallQuantum)effect).key);
      }
    }

    if (callKeys.isEmpty()) {
      solved.put(key, effects);
      moving.add(key);
    } else {
      pending.put(key, effects);
      for (HKey callKey : callKeys) {
        Set<HKey> deps = dependencies.get(callKey);
        if (deps == null) {
          deps = new HashSet<HKey>();
          dependencies.put(callKey, deps);
        }
        deps.add(key);
      }
    }
  }

	public Map<HKey, Set<HEffectQuantum>> solve() {
    while (!moving.isEmpty()) {
      HKey key = moving.pop();
      Set<HEffectQuantum> effects = solved.get(key);

      HKey[] propagateKeys;
      Set[] propagateEffects;

      if (key.stable) {
        propagateKeys = new HKey[]{key, key.mkUnstable()};
        propagateEffects = new Set[]{effects, effects};
      }
      else {
        propagateKeys = new HKey[]{key.mkStable(), key};
        propagateEffects = new Set[]{effects, mkUnstableEffects(key)};
      }
      for (int i = 0; i < propagateKeys.length; i++) {
        HKey pKey = propagateKeys[i];
        Set<HEffectQuantum> pEffects = propagateEffects[i];
        Set<HKey> dKeys = dependencies.remove(pKey);
        if (dKeys != null) {
          for (HKey dKey : dKeys) {
            Set<HEffectQuantum> dEffects = pending.remove(dKey);
            if (dEffects == null) {
              // already solved, for example, solution is top
              continue;
            }
            Set<HKey> callKeys = new HashSet<HKey>();
            Set<HEffectQuantum> newEffects = new HashSet<HEffectQuantum>();
            Set<HEffectQuantum> delta = null;

            for (HEffectQuantum dEffect : dEffects) {
              if (dEffect instanceof HEffectQuantum.CallQuantum) {
                HEffectQuantum.CallQuantum call = ((HEffectQuantum.CallQuantum)dEffect);
                if (call.key.equals(pKey)) {
                  delta = substitute(pEffects, call.data, call.isStatic);
                  newEffects.addAll(delta);
                }
                else {
                  callKeys.add(call.key);
                  newEffects.add(call);
                }
              }
              else {
                newEffects.add(dEffect);
              }
            }

            if (PurityAnalysis.topHEffect.equals(delta)) {
              solved.put(dKey, PurityAnalysis.topHEffect);
              moving.push(dKey);
            }
            else if (callKeys.isEmpty()) {
              solved.put(dKey, newEffects);
              moving.push(dKey);
            }
            else {
              pending.put(dKey, newEffects);
            }
          }


        }
      }

    }
    return solved;
  }

  private Set<HEffectQuantum> substitute(Set<HEffectQuantum> effects, DataValue[] data, boolean isStatic) {
    if (effects.isEmpty() || PurityAnalysis.topHEffect.equals(effects)) {
      return effects;
    }
    else {
      Set<HEffectQuantum> newEffects = new HashSet<HEffectQuantum>();
      int shift = isStatic ? 0 : 1;
      for (HEffectQuantum effect : effects) {
        if (effect == HEffectQuantum.ThisChangeQuantum) {
          DataValue thisArg = data[0];
          if (thisArg == DataValue.ThisDataValue || thisArg == DataValue.OwnedDataValue) {
            newEffects.add(HEffectQuantum.ThisChangeQuantum);
          }
          else if (thisArg == DataValue.LocalDataValue) {
            // nothing
          }
          else if (thisArg instanceof DataValue.ParameterDataValue) {
            newEffects.add(new HEffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue)thisArg).n));
          }
          else {
            return PurityAnalysis.topHEffect;
          }
        }
        else if (effect instanceof HEffectQuantum.ParamChangeQuantum) {
          HEffectQuantum.ParamChangeQuantum paramChange = ((HEffectQuantum.ParamChangeQuantum)effect);
          DataValue paramArg = data[paramChange.n + shift];
          if (paramArg == DataValue.ThisDataValue || paramArg == DataValue.OwnedDataValue) {
            newEffects.add(HEffectQuantum.ThisChangeQuantum);
          }
          else if (paramArg == DataValue.LocalDataValue) {
            // nothing
          }
          else if (paramArg instanceof DataValue.ParameterDataValue) {
            newEffects.add(new HEffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue)paramArg).n));
          }
          else {
            return PurityAnalysis.topHEffect;
          }
        }
      }
      return newEffects;
    }
  }

  private static Set mkUnstableEffects(HKey key) {
    Set<EffectQuantum> hardcodedEffects = HardCodedPurity.getHardCodedSolution(key);
    return hardcodedEffects == null ? PurityAnalysis.topHEffect : hardcodedEffects;
  }
}
