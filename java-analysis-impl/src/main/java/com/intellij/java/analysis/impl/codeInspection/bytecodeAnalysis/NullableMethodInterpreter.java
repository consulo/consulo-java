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

import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm.InterpreterExt;
import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.InsnList;
import consulo.internal.org.objectweb.asm.tree.JumpInsnNode;
import consulo.internal.org.objectweb.asm.tree.MethodInsnNode;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicInterpreter;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;

import java.util.List;

class NullableMethodInterpreter extends BasicInterpreter implements InterpreterExt<Constraint> {
  private final InsnList insns;
  private final boolean[] origins;
  private final int[] originsMapping;
  final EKey[] keys;

  Constraint constraint;
  int delta;
  int nullsDelta;
  int notNullInsn = -1;
  int notNullCall;
  int notNullNull;

  NullableMethodInterpreter(InsnList insns, boolean[] origins, int[] originsMapping) {
    super(Opcodes.API_VERSION);
    this.insns = insns;
    this.origins = origins;
    this.originsMapping = originsMapping;
    keys = new EKey[originsMapping.length];
  }

  @Override
  public BasicValue newValue(Type type) {
    return ASMUtils.isThisType(type) ? ASMUtils.THIS_VALUE : super.newValue(type);
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException {
    if (insn.getOpcode() == Opcodes.ACONST_NULL) {
      int insnIndex = insns.indexOf(insn);
      if (origins[insnIndex]) {
        return new LabeledNull(1 << originsMapping[insnIndex]);
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
        if (value instanceof Calls) {
          delta = ((Calls) value).mergedLabels;
        }
        break;
      case IFNULL:
        if (value instanceof Calls) {
          notNullInsn = insns.indexOf(insn) + 1;
          notNullCall = ((Calls) value).mergedLabels;
        } else if (value instanceof LabeledNull) {
          notNullInsn = insns.indexOf(insn) + 1;
          notNullNull = ((LabeledNull) value).origins;
        }
        break;
      case IFNONNULL:
        if (value instanceof Calls) {
          notNullInsn = insns.indexOf(((JumpInsnNode) insn).label);
          notNullCall = ((Calls) value).mergedLabels;
        } else if (value instanceof LabeledNull) {
          notNullInsn = insns.indexOf(((JumpInsnNode) insn).label);
          notNullNull = ((LabeledNull) value).origins;
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
        if (value1 instanceof Calls) {
          delta = ((Calls) value1).mergedLabels;
        }
        if (value1 instanceof LabeledNull) {
          nullsDelta = ((LabeledNull) value1).origins;
        }
        break;
      default:
    }
    return super.binaryOperation(insn, value1, value2);
  }

  @Override
  public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3) {
    if (value1 instanceof Calls) {
      delta = ((Calls) value1).mergedLabels;
    }
    if (value1 instanceof LabeledNull) {
      nullsDelta = ((LabeledNull) value1).origins;
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
        if (receiver instanceof Calls) {
          delta = ((Calls) receiver).mergedLabels;
        }
        if (receiver instanceof LabeledNull) {
          nullsDelta = ((LabeledNull) receiver).origins;
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
          MethodInsnNode mNode = ((MethodInsnNode) insn);
          Member method = new Member(mNode.owner, mNode.name, mNode.desc);
          int label = 1 << originsMapping[insnIndex];
          if (keys[insnIndex] == null) {
            keys[insnIndex] = new EKey(method, Direction.NullableOut, stable);
          }
          return new Calls(label);
        }
        break;
      default:
    }
    return super.naryOperation(insn, values);
  }

  @Override
  public BasicValue merge(BasicValue v1, BasicValue v2) {
    if (v1 instanceof LabeledNull) {
      if (v2 instanceof LabeledNull) {
        return new LabeledNull(((LabeledNull) v1).origins | ((LabeledNull) v2).origins);
      } else {
        return v1;
      }
    } else if (v2 instanceof LabeledNull) {
      return v2;
    } else if (v1 instanceof Calls) {
      if (v2 instanceof Calls) {
        Calls calls1 = (Calls) v1;
        Calls calls2 = (Calls) v2;
        return new Calls(calls1.mergedLabels | calls2.mergedLabels);
      } else {
        return v1;
      }
    } else if (v2 instanceof Calls) {
      return v2;
    }
    return super.merge(v1, v2);
  }

  // ---------- InterpreterExt<Constraint> --------------

  @Override
  public void init(Constraint previous) {
    constraint = previous;
    delta = 0;
    nullsDelta = 0;

    notNullInsn = -1;
    notNullCall = 0;
    notNullNull = 0;
  }

  @Override
  public Constraint getAfterData(int insn) {
    Constraint afterData = mkAfterData();
    if (notNullInsn == insn) {
      return new Constraint(afterData.calls | notNullCall, afterData.nulls | notNullNull);
    }
    return afterData;
  }

  private Constraint mkAfterData() {
    if (delta == 0 && nullsDelta == 0 && notNullInsn == -1) {
      return constraint;
    }
    return new Constraint(constraint.calls | delta, constraint.nulls | nullsDelta);
  }

  @Override
  public Constraint merge(Constraint data1, Constraint data2) {
    return data1.equals(data2) ? data1 : new Constraint(data1.calls | data2.calls, data1.nulls | data2.nulls);
  }
}