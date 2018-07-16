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

package com.intellij.codeInspection.bytecodeAnalysis.asm;

import static org.objectweb.asm.Opcodes.*;

import java.util.List;

import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.FieldInsnNode;
import consulo.internal.org.objectweb.asm.tree.InvokeDynamicInsnNode;
import consulo.internal.org.objectweb.asm.tree.LdcInsnNode;
import consulo.internal.org.objectweb.asm.tree.MethodInsnNode;
import consulo.internal.org.objectweb.asm.tree.MethodNode;
import consulo.internal.org.objectweb.asm.tree.analysis.Interpreter;

class ParametersUsage extends Interpreter<ParamsValue>
{
  final ParamsValue val1;
  final ParamsValue val2;
  int called = -1;
  final int rangeStart;
  final int rangeEnd;
  final int arity;
  final int shift;

  ParametersUsage(MethodNode methodNode) {
    super(ASM5);
    arity = Type.getArgumentTypes(methodNode.desc).length;
    boolean[] emptyParams = new boolean[arity];
    val1 = new ParamsValue(emptyParams, 1);
    val2 = new ParamsValue(emptyParams, 2);

    shift = (methodNode.access & ACC_STATIC) == 0 ? 2 : 1;
    rangeStart = shift;
    rangeEnd = arity + shift;
  }

  @Override
  public ParamsValue newValue(Type type) {
    if (type == null) return val1;
    called++;
    if (type == Type.VOID_TYPE) return null;
    if (called < rangeEnd && rangeStart <= called && (ASMUtils.isReferenceType(type) || ASMUtils.isBooleanType(type))) {
      boolean[] params = new boolean[arity];
      params[called - shift] = true;
      return type.getSize() == 1 ? new ParamsValue(params, 1) : new ParamsValue(params, 2);
    }
    else {
      return type.getSize() == 1 ? val1 : val2;
    }
  }

  @Override
  public ParamsValue newOperation(final AbstractInsnNode insn) {
    int size;
    switch (insn.getOpcode()) {
      case LCONST_0:
      case LCONST_1:
      case DCONST_0:
      case DCONST_1:
        size = 2;
        break;
      case LDC:
        Object cst = ((LdcInsnNode) insn).cst;
        size = cst instanceof Long || cst instanceof Double ? 2 : 1;
        break;
      case GETSTATIC:
        size = Type.getType(((FieldInsnNode) insn).desc).getSize();
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public ParamsValue copyOperation(AbstractInsnNode insn, ParamsValue value) {
    return value;
  }

  @Override
  public ParamsValue unaryOperation(AbstractInsnNode insn, ParamsValue value) {
    int size;
    switch (insn.getOpcode()) {
      case CHECKCAST:
        return value;
      case LNEG:
      case DNEG:
      case I2L:
      case I2D:
      case L2D:
      case F2L:
      case F2D:
      case D2L:
        size = 2;
        break;
      case GETFIELD:
        size = Type.getType(((FieldInsnNode) insn).desc).getSize();
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public ParamsValue binaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2) {
    int size;
    switch (insn.getOpcode()) {
      case LALOAD:
      case DALOAD:
      case LADD:
      case DADD:
      case LSUB:
      case DSUB:
      case LMUL:
      case DMUL:
      case LDIV:
      case DDIV:
      case LREM:
      case DREM:
      case LSHL:
      case LSHR:
      case LUSHR:
      case LAND:
      case LOR:
      case LXOR:
        size = 2;
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public ParamsValue ternaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2, ParamsValue value3) {
    return null;
  }

  @Override
  public ParamsValue naryOperation(AbstractInsnNode insn, List<? extends ParamsValue> values) {
    int size;
    int opcode = insn.getOpcode();
    if (opcode == MULTIANEWARRAY) {
      size = 1;
    } else {
      String desc = (opcode == INVOKEDYNAMIC) ? ((InvokeDynamicInsnNode) insn).desc : ((MethodInsnNode) insn).desc;
      size = Type.getReturnType(desc).getSize();
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public void returnOperation(AbstractInsnNode insn, ParamsValue value, ParamsValue expected) {}

  @Override
  public ParamsValue merge(ParamsValue v1, ParamsValue v2) {
    if (v1.equals(v2)) return v1;
    boolean[] params = new boolean[arity];
    boolean[] params1 = v1.params;
    boolean[] params2 = v2.params;
    for (int i = 0; i < arity; i++) {
      params[i] = params1[i] || params2[i];
    }
    return new ParamsValue(params, Math.min(v1.size, v2.size));
  }
}
