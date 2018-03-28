/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import javax.annotation.Nonnull;

import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.InsnList;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.Analyzer;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame;
import org.jetbrains.org.objectweb.asm.tree.analysis.Value;

/**
 * @author lambdamix
 */
public class LeakingParameters {
  public final Frame<Value>[] frames;
  public final boolean[] parameters;
  public final boolean[] nullableParameters;

  public LeakingParameters(Frame<Value>[] frames, boolean[] parameters, boolean[] nullableParameters) {
    this.frames = frames;
    this.parameters = parameters;
    this.nullableParameters = nullableParameters;
  }

  @Nonnull
  public static LeakingParameters build(String className, MethodNode methodNode, boolean jsr) throws AnalyzerException {
    Frame<ParamsValue>[] frames = jsr ?
                                  new Analyzer<ParamsValue>(new ParametersUsage(methodNode)).analyze(className, methodNode) :
                                  new LiteAnalyzer<ParamsValue>(new ParametersUsage(methodNode)).analyze(className, methodNode);
    InsnList insns = methodNode.instructions;
    LeakingParametersCollector collector = new LeakingParametersCollector(methodNode);
    for (int i = 0; i < frames.length; i++) {
      AbstractInsnNode insnNode = insns.get(i);
      Frame<ParamsValue> frame = frames[i];
      if (frame != null) {
        switch (insnNode.getType()) {
          case AbstractInsnNode.LABEL:
          case AbstractInsnNode.LINE:
          case AbstractInsnNode.FRAME:
            break;
          default:
            new Frame<ParamsValue>(frame).execute(insnNode, collector);
        }
      }
    }
    boolean[] notNullParameters = collector.leaking;
    boolean[] nullableParameters = collector.nullableLeaking;
    for (int i = 0; i < nullableParameters.length; i++) {
      nullableParameters[i] |= notNullParameters[i];
    }
    return new LeakingParameters((Frame<Value>[])(Frame<?>[])frames, notNullParameters, nullableParameters);
  }

  @Nonnull
  public static LeakingParameters buildFast(String className, MethodNode methodNode, boolean jsr) throws AnalyzerException {
    IParametersUsage parametersUsage = new IParametersUsage(methodNode);
    Frame<?>[] frames = jsr ?
                        new Analyzer<IParamsValue>(parametersUsage).analyze(className, methodNode) :
                        new LiteAnalyzer<IParamsValue>(parametersUsage).analyze(className, methodNode);
    int leakingMask = parametersUsage.leaking;
    int nullableLeakingMask = parametersUsage.nullableLeaking;
    boolean[] notNullParameters = new boolean[parametersUsage.arity];
    boolean[] nullableParameters = new boolean[parametersUsage.arity];
    for (int i = 0; i < notNullParameters.length; i++) {
      notNullParameters[i] = (leakingMask & (1 << i)) != 0;
      nullableParameters[i] = ((leakingMask | nullableLeakingMask) & (1 << i)) != 0;
    }
    return new LeakingParameters((Frame<Value>[])frames, notNullParameters, nullableParameters);
  }
}

