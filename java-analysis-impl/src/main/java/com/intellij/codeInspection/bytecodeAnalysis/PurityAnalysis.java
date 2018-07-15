/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;

/**
 * Produces equations for inference of @Contract(pure=true) annotations.
 * Scala source at https://github.com/ilya-klyuchnikov/faba
 * Algorithm: https://github.com/ilya-klyuchnikov/faba/blob/ef1c15b4758517652e939f67099bbec0260e9e68/notes/purity.md
 */
public class PurityAnalysis {
  static final Set<EffectQuantum> topEffect = Collections.singleton(EffectQuantum.TopEffectQuantum);
  static final Set<HEffectQuantum> topHEffect = Collections.singleton(HEffectQuantum.TopEffectQuantum);

  static final int UN_ANALYZABLE_FLAG = Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_INTERFACE;

  @Nonnull
  public static Equation analyze(Method method, MethodNode methodNode, boolean stable) {
    Key key = new Key(method, Direction.Pure, stable);
    Set<EffectQuantum> hardCodedSolution = HardCodedPurity.getHardCodedSolution(key);
    if (hardCodedSolution != null) {
      return new Equation(key, new Effects(hardCodedSolution));
    }

    if ((methodNode.access & UN_ANALYZABLE_FLAG) != 0) {
      return new Equation(key, new Effects(topEffect));
    }

    DataInterpreter dataInterpreter = new DataInterpreter(methodNode);
    try {
      new Analyzer<DataValue>(dataInterpreter).analyze("this", methodNode);
    }
    catch (AnalyzerException e) {
      return new Equation(key, new Effects(topEffect));
    }
    EffectQuantum[] quanta = dataInterpreter.effects;
    Set<EffectQuantum> effects = new HashSet<EffectQuantum>();
    for (EffectQuantum effectQuantum : quanta) {
      if (effectQuantum != null) {
        if (effectQuantum == EffectQuantum.TopEffectQuantum) {
          return new Equation(key, new Effects(topEffect));
        }
        effects.add(effectQuantum);
      }
    }
    return new Equation(key, new Effects(effects));
  }
}

