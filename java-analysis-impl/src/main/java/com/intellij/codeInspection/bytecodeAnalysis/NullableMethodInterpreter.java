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

import static com.intellij.codeInspection.bytecodeAnalysis.NullableMethodAnalysisData.ThisType;
import static com.intellij.codeInspection.bytecodeAnalysis.NullableMethodAnalysisData.ThisValue;

import java.util.List;

import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.InsnList;
import org.jetbrains.org.objectweb.asm.tree.JumpInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;
import com.intellij.codeInspection.bytecodeAnalysis.asm.InterpreterExt;

class NullableMethodInterpreter extends BasicInterpreter implements InterpreterExt<NullableMethodAnalysisData.Constraint>
{
  final InsnList insns;
  final boolean[] origins;
  private final int[] originsMapping;
  final Key[] keys;

  NullableMethodAnalysisData.Constraint constraint;
  int delta;
  int nullsDelta;
  int notNullInsn = -1;
  int notNullCall;
  int notNullNull;

  NullableMethodInterpreter(InsnList insns, boolean[] origins, int[] originsMapping) {
    this.insns = insns;
    this.origins = origins;
    this.originsMapping = originsMapping;
    keys = new Key[originsMapping.length];
  }

	@Override
  public BasicValue newValue(Type type) {
    return ThisType.equals(type) ? ThisValue : super.newValue(type);
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException
  {
    if (insn.getOpcode() == Opcodes.ACONST_NULL) {
      int insnIndex = insns.indexOf(insn);
      if (origins[insnIndex]) {
        return new NullableMethodAnalysisData.LabeledNull(1 << originsMapping[insnIndex]);
      }
    }
    return super.newOperation(insn);
  }

  @Override
  public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case GETFIELD:
      case ARRAYLENGTH:
      case MONITORENTER:
        if (value instanceof NullableMethodAnalysisData.Calls) {
          delta = ((NullableMethodAnalysisData.Calls)value).mergedLabels;
        }
        break;
      case IFNULL:
        if (value instanceof NullableMethodAnalysisData.Calls) {
          notNullInsn = insns.indexOf(insn) + 1;
          notNullCall = ((NullableMethodAnalysisData.Calls)value).mergedLabels;
        }
        else if (value instanceof NullableMethodAnalysisData.LabeledNull) {
          notNullInsn = insns.indexOf(insn) + 1;
          notNullNull = ((NullableMethodAnalysisData.LabeledNull)value).origins;
        }
        break;
      case IFNONNULL:
        if (value instanceof NullableMethodAnalysisData.Calls) {
          notNullInsn = insns.indexOf(((JumpInsnNode)insn).label);
          notNullCall = ((NullableMethodAnalysisData.Calls)value).mergedLabels;
        }
        else if (value instanceof NullableMethodAnalysisData.LabeledNull) {
          notNullInsn = insns.indexOf(((JumpInsnNode)insn).label);
          notNullNull = ((NullableMethodAnalysisData.LabeledNull)value).origins;
        }
        break;
      default:

    }
    return super.unaryOperation(insn, value);
  }

  @Override
  public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case PUTFIELD:
      case IALOAD:
      case LALOAD:
      case FALOAD:
      case DALOAD:
      case AALOAD:
      case BALOAD:
      case CALOAD:
      case SALOAD:
        if (value1 instanceof NullableMethodAnalysisData.Calls) {
          delta = ((NullableMethodAnalysisData.Calls)value1).mergedLabels;
        }
        if (value1 instanceof NullableMethodAnalysisData.LabeledNull){
          nullsDelta = ((NullableMethodAnalysisData.LabeledNull)value1).origins;
        }
        break;
      default:
    }
    return super.binaryOperation(insn, value1, value2);
  }

  @Override
  public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3)
    throws AnalyzerException {
    if (value1 instanceof NullableMethodAnalysisData.Calls) {
      delta = ((NullableMethodAnalysisData.Calls)value1).mergedLabels;
    }
    if (value1 instanceof NullableMethodAnalysisData.LabeledNull){
      nullsDelta = ((NullableMethodAnalysisData.LabeledNull)value1).origins;
    }
    return null;
  }

  @Override
  public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
    int opCode = insn.getOpcode();
    switch (opCode) {
      case INVOKESPECIAL:
      case INVOKEINTERFACE:
      case INVOKEVIRTUAL:
        BasicValue receiver = values.get(0);
        if (receiver instanceof NullableMethodAnalysisData.Calls) {
          delta = ((NullableMethodAnalysisData.Calls)receiver).mergedLabels;
        }
        if (receiver instanceof NullableMethodAnalysisData.LabeledNull){
          nullsDelta = ((NullableMethodAnalysisData.LabeledNull)receiver).origins;
        }
        break;
      default:
    }

    switch (opCode) {
      case INVOKESTATIC:
      case INVOKESPECIAL:
      case INVOKEVIRTUAL:
        int insnIndex = insns.indexOf(insn);
        if (origins[insnIndex]) {
          boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
          MethodInsnNode mNode = ((MethodInsnNode)insn);
          Method method = new Method(mNode.owner, mNode.name, mNode.desc);
          int label = 1 << originsMapping[insnIndex];
          if (keys[insnIndex] == null) {
            keys[insnIndex] = new Key(method, Direction.NullableOut, stable);
          }
          return new NullableMethodAnalysisData.Calls(label);
        }
        break;
      default:
    }
    return super.naryOperation(insn, values);
  }

  @Override
  public BasicValue merge(BasicValue v1, BasicValue v2) {
    if (v1 instanceof NullableMethodAnalysisData.LabeledNull) {
      if (v2 instanceof NullableMethodAnalysisData.LabeledNull) {
        return new NullableMethodAnalysisData.LabeledNull(((NullableMethodAnalysisData.LabeledNull)v1).origins | ((NullableMethodAnalysisData.LabeledNull)v2).origins);
      }
      else {
        return v1;
      }
    }
    else if (v2 instanceof NullableMethodAnalysisData.LabeledNull) {
      return v2;
    }
    else if (v1 instanceof NullableMethodAnalysisData.Calls) {
      if (v2 instanceof NullableMethodAnalysisData.Calls) {
        NullableMethodAnalysisData.Calls calls1 = (NullableMethodAnalysisData.Calls)v1;
        NullableMethodAnalysisData.Calls calls2 = (NullableMethodAnalysisData.Calls)v2;
        return new NullableMethodAnalysisData.Calls(calls1.mergedLabels | calls2.mergedLabels);
      }
      else {
        return v1;
      }
    }
    else if (v2 instanceof NullableMethodAnalysisData.Calls) {
      return v2;
    }
    return super.merge(v1, v2);
  }

  // ---------- InterpreterExt<Constraint> --------------

  @Override
  public void init(NullableMethodAnalysisData.Constraint previous) {
    constraint = previous;
    delta = 0;
    nullsDelta = 0;

    notNullInsn = -1;
    notNullCall = 0;
    notNullNull = 0;
  }

	@Override
  public NullableMethodAnalysisData.Constraint getAfterData(int insn) {
    NullableMethodAnalysisData.Constraint afterData = mkAfterData();
    if (notNullInsn == insn) {
      return new NullableMethodAnalysisData.Constraint(afterData.calls | notNullCall, afterData.nulls | notNullNull);
    }
    return afterData;
  }

	private NullableMethodAnalysisData.Constraint mkAfterData() {
    if (delta == 0 && nullsDelta == 0 && notNullInsn == -1) {
      return constraint;
    }
    return new NullableMethodAnalysisData.Constraint(constraint.calls | delta, constraint.nulls | nullsDelta);
  }

	@Override
  public NullableMethodAnalysisData.Constraint merge(NullableMethodAnalysisData.Constraint data1, NullableMethodAnalysisData.Constraint data2) {
    if (data1.equals(data2)) {
      return data1;
    } else {
      return new NullableMethodAnalysisData.Constraint(data1.calls | data2.calls, data1.nulls | data2.nulls);
    }
  }
}
