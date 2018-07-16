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

class IParametersUsage extends Interpreter<IParamsValue>
{
  static final IParamsValue val1 = new IParamsValue(0, 1);
  static final IParamsValue val2 = new IParamsValue(0, 2);
  int leaking;
  int nullableLeaking;
  int called = -1;
  final int rangeStart;
  final int rangeEnd;
  final int arity;
  final int shift;

  IParametersUsage(MethodNode methodNode) {
    super(ASM5);
    arity = Type.getArgumentTypes(methodNode.desc).length;
    shift = (methodNode.access & ACC_STATIC) == 0 ? 2 : 1;
    rangeStart = shift;
    rangeEnd = arity + shift;
  }

  @Override
  public IParamsValue newValue(Type type) {
    if (type == null) return val1;
    called++;
    if (type == Type.VOID_TYPE) return null;
    if (called < rangeEnd && rangeStart <= called && (ASMUtils.isReferenceType(type) || ASMUtils.isBooleanType(type))) {
      int n = called - shift;
      return type.getSize() == 1 ? new IParamsValue(1 << n, 1) : new IParamsValue(1 << n, 2);
    }
    else {
      return type.getSize() == 1 ? val1 : val2;
    }
  }

  @Override
  public IParamsValue newOperation(final AbstractInsnNode insn) {
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
        size = ASMUtils.getSizeFast(((FieldInsnNode)insn).desc);
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public IParamsValue copyOperation(AbstractInsnNode insn, IParamsValue value) {
    return value;
  }

  @Override
  public IParamsValue unaryOperation(AbstractInsnNode insn, IParamsValue value) {
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
        size = ASMUtils.getSizeFast(((FieldInsnNode)insn).desc);
        leaking |= value.params;
        break;
      case ARRAYLENGTH:
      case MONITORENTER:
      case INSTANCEOF:
      case IRETURN:
      case ARETURN:
      case IFNONNULL:
      case IFNULL:
      case IFEQ:
      case IFNE:
        size = 1;
        leaking |= value.params;
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public IParamsValue binaryOperation(AbstractInsnNode insn, IParamsValue value1, IParamsValue value2) {
    int size;
    switch (insn.getOpcode()) {
      case LALOAD:
      case DALOAD:
        size = 2;
        leaking |= value1.params;
        break;
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
      case IALOAD:
      case FALOAD:
      case AALOAD:
      case BALOAD:
      case CALOAD:
      case SALOAD:
        leaking |= value1.params;
        size = 1;
        break;
      case PUTFIELD:
        leaking |= value1.params;
        nullableLeaking |= value2.params;
        size = 1;
        break;
      default:
        size = 1;
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public IParamsValue ternaryOperation(AbstractInsnNode insn, IParamsValue value1, IParamsValue value2, IParamsValue value3) {
    switch (insn.getOpcode()) {
      case IASTORE:
      case LASTORE:
      case FASTORE:
      case DASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
        leaking |= value1.params;
        break;
      case AASTORE:
        leaking |= value1.params;
        nullableLeaking |= value3.params;
        break;
      default:
    }
    return null;
  }

  @Override
  public IParamsValue naryOperation(AbstractInsnNode insn, List<? extends IParamsValue> values) {
    int opcode = insn.getOpcode();
    switch (opcode) {
      case INVOKESTATIC:
      case INVOKESPECIAL:
      case INVOKEVIRTUAL:
      case INVOKEINTERFACE:
        for (IParamsValue value : values) {
          leaking |= value.params;
        }
        break;
      default:
    }
    int size;
    if (opcode == MULTIANEWARRAY) {
      size = 1;
    } else {
      String desc = (opcode == INVOKEDYNAMIC) ? ((InvokeDynamicInsnNode) insn).desc : ((MethodInsnNode) insn).desc;
      size = ASMUtils.getReturnSizeFast(desc);
    }
    return size == 1 ? val1 : val2;
  }

  @Override
  public void returnOperation(AbstractInsnNode insn, IParamsValue value, IParamsValue expected) {}

  @Override
  public IParamsValue merge(IParamsValue v1, IParamsValue v2) {
    if (v1.equals(v2)) return v1;
    return new IParamsValue(v1.params | v2.params, Math.min(v1.size, v2.size));
  }
}
