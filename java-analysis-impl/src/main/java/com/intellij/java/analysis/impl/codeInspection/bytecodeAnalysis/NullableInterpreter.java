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

import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.NullValue;

class NullableInterpreter extends NullityInterpreter {
  NullableInterpreter() {
    super(true, true);
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
    if (insn.getOpcode() == ACONST_NULL && taken) {
      return NullValue;
    }
    return super.newOperation(insn);
  }

  @Override
  PResults.PResult combine(PResults.PResult res1, PResults.PResult res2) throws AnalyzerException {
    return PResults.join(res1, res2);
  }
}