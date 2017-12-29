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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.intellij.openapi.util.Couple;


final class HardCodedPurity {
  static Set<Couple<String>> ownedFields = new HashSet<Couple<String>>();
  static Map<Method, Set<EffectQuantum>> solutions = new HashMap<Method, Set<EffectQuantum>>();
  static Set<EffectQuantum> thisChange = Collections.singleton(EffectQuantum.ThisChangeQuantum);
  static {
    ownedFields.add(new Couple<String>("java/lang/AbstractStringBuilder", "value"));

    solutions.put(new Method("java/lang/Throwable", "fillInStackTrace", "(I)Ljava/lang/Throwable;"), thisChange);
    solutions.put(new Method("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V"), Collections.<EffectQuantum>singleton(new EffectQuantum.ParamChangeQuantum(2)));
    solutions.put(new Method("java/lang/AbstractStringBuilder", "expandCapacity", "(I)V"), thisChange);
    solutions.put(new Method("java/lang/StringBuilder", "expandCapacity", "(I)V"), thisChange);
    solutions.put(new Method("java/lang/StringBuffer", "expandCapacity", "(I)V"), thisChange);
    solutions.put(new Method("java/lang/StringIndexOutOfBoundsException", "<init>", "(I)V"), thisChange);
  }

  static Set<EffectQuantum> getHardCodedSolution(Key key) {
    Method method = key.method;
    if (method.methodName.equals("fillInStackTrace") && method.methodDesc.equals("()Ljava/lang/Throwable;")) {
      return thisChange;
    }
    return solutions.get(key.method);
  }

  static Set<EffectQuantum> getHardCodedSolution(HKey key) {
    // TODO: implement the logic as in https://github.com/ilya-klyuchnikov/faba/blob/2ffab410416e0a9f8e35d5071df50bcf27b1e149/src/main/scala/asm/purity.scala#L238
    // The problem with porting logic from Scala version "as is" is that in Scala version original keys (Key) are used.
    // Here (in IDEA) the hashed keys (HKey) are used. In a general hashed keys may lead to collisions.
    // So in order to port the logic, hardcoded solutions should be used with stable keys,
    // that is - during analysis - com.intellij.codeInspection.bytecodeAnalysis.DataInterpreter.naryOperation
    return null;
  }
}
