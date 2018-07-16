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

import java.util.List;

import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.FieldInsnNode;
import consulo.internal.org.objectweb.asm.tree.InvokeDynamicInsnNode;
import consulo.internal.org.objectweb.asm.tree.LdcInsnNode;
import consulo.internal.org.objectweb.asm.tree.MethodInsnNode;
import consulo.internal.org.objectweb.asm.tree.MethodNode;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.Interpreter;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.openapi.util.Couple;

class DataInterpreter extends Interpreter<DataValue>
{
  private int called = -1;
  private final MethodNode methodNode;
  private final int shift;
  final int rangeStart;
  final int rangeEnd;
  final int arity;
  final EffectQuantum[] effects;

  protected DataInterpreter(MethodNode methodNode) {
    super(Opcodes.API_VERSION);
    this.methodNode = methodNode;
    shift = (methodNode.access & Opcodes.ACC_STATIC) == 0 ? 2 : 1;
    arity = Type.getArgumentTypes(methodNode.desc).length;
    rangeStart = shift;
    rangeEnd = arity + shift;
    effects = new EffectQuantum[methodNode.instructions.size()];
  }

  @Override
  public DataValue newValue(Type type) {
    if (type == null) {
      return DataValue.UnknownDataValue1;
    }
    called += 1;
    if (type.toString().equals("Lthis;")) {
      return DataValue.ThisDataValue;
    } else if (called < rangeEnd && rangeStart <= called) {
      if (type == Type.VOID_TYPE) {
        return null;
      } else if (ASMUtils.isReferenceType(type)) {
        return new DataValue.ParameterDataValue(called - shift);
      } else {
        return type.getSize() == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      }
    } else {
      if (type == Type.VOID_TYPE) {
        return null;
      } else {
        return type.getSize() == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      }
    }
  }

  @Override
  public DataValue newOperation(AbstractInsnNode insn) throws AnalyzerException
  {
    switch (insn.getOpcode()) {
      case Opcodes.NEW:
        return DataValue.LocalDataValue;
      case Opcodes.LCONST_0:
      case Opcodes.LCONST_1:
      case Opcodes.DCONST_0:
      case Opcodes.DCONST_1:
        return DataValue.UnknownDataValue2;
      case Opcodes.LDC:
        Object cst = ((LdcInsnNode)insn).cst;
        int size = (cst instanceof Long || cst instanceof Double) ? 2 : 1;
        return size == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      case Opcodes.GETSTATIC:
        size = Type.getType(((FieldInsnNode)insn).desc).getSize();
        return size == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      default:
        return DataValue.UnknownDataValue1;
    }
  }

  @Override
  public DataValue binaryOperation(AbstractInsnNode insn, DataValue value1, DataValue value2) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case Opcodes.LALOAD:
      case Opcodes.DALOAD:
      case Opcodes.LADD:
      case Opcodes.DADD:
      case Opcodes.LSUB:
      case Opcodes.DSUB:
      case Opcodes.LMUL:
      case Opcodes.DMUL:
      case Opcodes.LDIV:
      case Opcodes.DDIV:
      case Opcodes.LREM:
      case Opcodes.LSHL:
      case Opcodes.LSHR:
      case Opcodes.LUSHR:
      case Opcodes.LAND:
      case Opcodes.LOR:
      case Opcodes.LXOR:
        return DataValue.UnknownDataValue2;
      case Opcodes.PUTFIELD:
        final EffectQuantum effectQuantum;
        if (value1 == DataValue.ThisDataValue || value1 == DataValue.OwnedDataValue) {
          effectQuantum = EffectQuantum.ThisChangeQuantum;
        } else if (value1 == DataValue.LocalDataValue) {
          effectQuantum = null;
        } else if (value1 instanceof DataValue.ParameterDataValue) {
          effectQuantum = new EffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue)value1).n);
        } else {
          effectQuantum = EffectQuantum.TopEffectQuantum;
        }
        int insnIndex = methodNode.instructions.indexOf(insn);
        effects[insnIndex] = effectQuantum;
        return DataValue.UnknownDataValue1;
      default:
        return DataValue.UnknownDataValue1;
    }
  }

  @Override
  public DataValue copyOperation(AbstractInsnNode insn, DataValue value) throws AnalyzerException {
    return value;
  }

  @Override
  public DataValue naryOperation(AbstractInsnNode insn, List<? extends DataValue> values) throws AnalyzerException {
    int insnIndex = methodNode.instructions.indexOf(insn);
    int opCode = insn.getOpcode();
    switch (opCode) {
      case Opcodes.MULTIANEWARRAY:
        return DataValue.LocalDataValue;
      case Opcodes.INVOKEDYNAMIC:
        effects[insnIndex] = EffectQuantum.TopEffectQuantum;
        return (ASMUtils.getReturnSizeFast(((InvokeDynamicInsnNode)insn).desc) == 1) ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
      case Opcodes.INVOKEVIRTUAL:
      case Opcodes.INVOKESPECIAL:
      case Opcodes.INVOKESTATIC:
      case Opcodes.INVOKEINTERFACE:
        boolean stable = opCode == Opcodes.INVOKESPECIAL || opCode == Opcodes.INVOKESTATIC;
        MethodInsnNode mNode = ((MethodInsnNode)insn);
        DataValue[] data = values.toArray(new DataValue[values.size()]);
        Key key = new Key(new Method(mNode.owner, mNode.name, mNode.desc), Direction.Pure, stable);
        effects[insnIndex] = new EffectQuantum.CallQuantum(key, data, opCode == Opcodes.INVOKESTATIC);
        return (ASMUtils.getReturnSizeFast(mNode.desc) == 1) ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
    }
    return null;
  }

  @Override
  public DataValue unaryOperation(AbstractInsnNode insn, DataValue value) throws AnalyzerException {

    switch (insn.getOpcode()) {
      case Opcodes.LNEG:
      case Opcodes.DNEG:
      case Opcodes.I2L:
      case Opcodes.I2D:
      case Opcodes.L2D:
      case Opcodes.F2L:
      case Opcodes.F2D:
      case Opcodes.D2L:
        return DataValue.UnknownDataValue2;
      case Opcodes.GETFIELD:
        FieldInsnNode fieldInsn = ((FieldInsnNode)insn);
        if (value == DataValue.ThisDataValue && HardCodedPurity.ownedFields.contains(new Couple<String>(fieldInsn.owner, fieldInsn.name))) {
          return DataValue.OwnedDataValue;
        } else {
          return ASMUtils.getSizeFast(fieldInsn.desc) == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
        }
      case Opcodes.CHECKCAST:
        return value;
      case Opcodes.PUTSTATIC:
        int insnIndex = methodNode.instructions.indexOf(insn);
        effects[insnIndex] = EffectQuantum.TopEffectQuantum;
        return DataValue.UnknownDataValue1;
      case Opcodes.NEWARRAY:
      case Opcodes.ANEWARRAY:
        return DataValue.LocalDataValue;
      default:
        return DataValue.UnknownDataValue1;
    }
  }

  @Override
  public DataValue ternaryOperation(AbstractInsnNode insn, DataValue value1, DataValue value2, DataValue value3) throws AnalyzerException {
    int insnIndex = methodNode.instructions.indexOf(insn);
    if (value1 == DataValue.ThisDataValue || value1 == DataValue.OwnedDataValue) {
      effects[insnIndex] = EffectQuantum.ThisChangeQuantum;
    } else if (value1 instanceof DataValue.ParameterDataValue) {
      effects[insnIndex] = new EffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue)value1).n);
    } else if (value1 == DataValue.LocalDataValue) {
      effects[insnIndex] = null;
    } else {
      effects[insnIndex] = EffectQuantum.TopEffectQuantum;
    }
    return DataValue.UnknownDataValue1;
  }

  @Override
  public void returnOperation(AbstractInsnNode insn, DataValue value, DataValue expected) throws AnalyzerException {

  }

  @Override
  public DataValue merge(DataValue v1, DataValue v2) {
    if (v1.equals(v2)) {
      return v1;
    } else {
      int size = Math.min(v1.getSize(), v2.getSize());
      return size == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
    }
  }
}
