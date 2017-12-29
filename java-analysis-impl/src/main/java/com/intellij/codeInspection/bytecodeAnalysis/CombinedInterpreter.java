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

import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.CLASS_VALUE;
import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.FalseValue;
import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.METHOD_HANDLE_VALUE;
import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.METHOD_VALUE;
import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.STRING_VALUE;
import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.TrueValue;
import static com.intellij.codeInspection.bytecodeAnalysis.CombinedData.ThisValue;

import java.util.List;
import java.util.Set;

import org.jetbrains.org.objectweb.asm.Handle;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.InsnList;
import org.jetbrains.org.objectweb.asm.tree.LdcInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode;
import org.jetbrains.org.objectweb.asm.tree.TypeInsnNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;
import com.intellij.util.containers.HashSet;

final class CombinedInterpreter extends BasicInterpreter
{
  // Parameters dereferenced during execution of a method, tracked by parameter's indices.
  // Dereferenced parameters are @NotNull.
  final boolean[] dereferencedParams;
  // Parameters, that are written to something or passed to an interface methods.
  // This parameters cannot be @Nullable.
  final boolean[] notNullableParams;
  // parameterFlow(i) for i-th parameter stores a set parameter positions it is passed to
  // parameter is @NotNull if any of its usages are @NotNull
  final Set<CombinedData.ParamKey>[] parameterFlow;

  // Trackable values that were dereferenced during execution of a method
  // Values are are identified by `origin` index
  final boolean[] dereferencedValues;
  private final InsnList insns;

  CombinedInterpreter(InsnList insns, int arity) {
    dereferencedParams = new boolean[arity];
    notNullableParams = new boolean[arity];
    parameterFlow = new Set[arity];
    this.insns = insns;
    dereferencedValues = new boolean[insns.size()];
  }

  private int insnIndex(AbstractInsnNode insn) {
    return insns.indexOf(insn);
  }

	private static BasicValue track(int origin, BasicValue basicValue) {
    return basicValue == null ? null : new CombinedData.TrackableValue(origin, basicValue.getType());
  }

  @Override
  public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException
  {
    int origin = insnIndex(insn);
    switch (insn.getOpcode()) {
      case ICONST_0:
        return FalseValue;
      case ICONST_1:
        return TrueValue;
      case ACONST_NULL:
        return new CombinedData.TrackableNullValue(origin);
      case LDC:
        Object cst = ((LdcInsnNode)insn).cst;
        if (cst instanceof Type) {
          Type type = (Type)cst;
          if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
            return CLASS_VALUE;
          }
          if (type.getSort() == Type.METHOD) {
            return METHOD_VALUE;
          }
        }
        else if (cst instanceof String) {
          return STRING_VALUE;
        }
        else if (cst instanceof Handle) {
          return METHOD_HANDLE_VALUE;
        }
        break;
      case NEW:
        return new AbstractValues.NotNullValue(Type.getObjectType(((TypeInsnNode)insn).desc));
      default:
    }
    return track(origin, super.newOperation(insn));
  }

  @Override
  public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
    int origin = insnIndex(insn);
    switch (insn.getOpcode()) {
      case GETFIELD:
      case ARRAYLENGTH:
      case MONITORENTER:
        if (value instanceof CombinedData.NthParamValue) {
          dereferencedParams[((CombinedData.NthParamValue)value).n] = true;
        }
        if (value instanceof CombinedData.Trackable) {
          dereferencedValues[((CombinedData.Trackable)value).getOriginInsnIndex()] = true;
        }
        return track(origin, super.unaryOperation(insn, value));
      case CHECKCAST:
        if (value instanceof CombinedData.NthParamValue) {
          return new CombinedData.NthParamValue(Type.getObjectType(((TypeInsnNode)insn).desc), ((CombinedData.NthParamValue)value).n);
        }
        break;
      case NEWARRAY:
      case ANEWARRAY:
        return new AbstractValues.NotNullValue(super.unaryOperation(insn, value).getType());
      default:
    }
    return track(origin, super.unaryOperation(insn, value));
  }

  @Override
  public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) throws AnalyzerException {
    switch (insn.getOpcode()) {
      case PUTFIELD:
        if (value1 instanceof CombinedData.NthParamValue) {
          dereferencedParams[((CombinedData.NthParamValue)value1).n] = true;
        }
        if (value1 instanceof CombinedData.Trackable) {
          dereferencedValues[((CombinedData.Trackable)value1).getOriginInsnIndex()] = true;
        }
        if (value2 instanceof CombinedData.NthParamValue) {
          notNullableParams[((CombinedData.NthParamValue)value2).n] = true;
        }
        break;
      case IALOAD:
      case LALOAD:
      case FALOAD:
      case DALOAD:
      case AALOAD:
      case BALOAD:
      case CALOAD:
      case SALOAD:
        if (value1 instanceof CombinedData.NthParamValue) {
          dereferencedParams[((CombinedData.NthParamValue)value1).n] = true;
        }
        if (value1 instanceof CombinedData.Trackable) {
          dereferencedValues[((CombinedData.Trackable)value1).getOriginInsnIndex()] = true;
        }
        break;
      default:
    }
    return track(insnIndex(insn), super.binaryOperation(insn, value1, value2));
  }

  @Override
  public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3)
    throws AnalyzerException {
    switch (insn.getOpcode()) {
      case IASTORE:
      case LASTORE:
      case FASTORE:
      case DASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
        if (value1 instanceof CombinedData.NthParamValue) {
          dereferencedParams[((CombinedData.NthParamValue)value1).n] = true;
        }
        if (value1 instanceof CombinedData.Trackable) {
          dereferencedValues[((CombinedData.Trackable)value1).getOriginInsnIndex()] = true;
        }
        break;
      case AASTORE:
        if (value1 instanceof CombinedData.NthParamValue) {
          dereferencedParams[((CombinedData.NthParamValue)value1).n] = true;
        }
        if (value1 instanceof CombinedData.Trackable) {
          dereferencedValues[((CombinedData.Trackable)value1).getOriginInsnIndex()] = true;
        }
        if (value3 instanceof CombinedData.NthParamValue) {
          notNullableParams[((CombinedData.NthParamValue)value3).n] = true;
        }
        break;
      default:
    }
    return null;
  }

  @Override
  public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
    int opCode = insn.getOpcode();
    int shift = opCode == INVOKESTATIC ? 0 : 1;
    int origin = insnIndex(insn);
    switch (opCode) {
      case INVOKESPECIAL:
      case INVOKEINTERFACE:
      case INVOKEVIRTUAL:
        BasicValue receiver = values.get(0);
        if (receiver instanceof CombinedData.NthParamValue) {
          dereferencedParams[((CombinedData.NthParamValue)receiver).n] = true;
        }
        if (receiver instanceof CombinedData.Trackable) {
          dereferencedValues[((CombinedData.Trackable)receiver).getOriginInsnIndex()] = true;
        }
      default:
    }

    switch (opCode) {
      case INVOKESTATIC:
      case INVOKESPECIAL:
      case INVOKEVIRTUAL:
      case INVOKEINTERFACE:
        boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
        MethodInsnNode mNode = (MethodInsnNode)insn;
        Method method = new Method(mNode.owner, mNode.name, mNode.desc);
        Type retType = Type.getReturnType(mNode.desc);

        for (int i = shift; i < values.size(); i++) {
          if (values.get(i) instanceof CombinedData.NthParamValue) {
            int n = ((CombinedData.NthParamValue)values.get(i)).n;
            if (opCode == INVOKEINTERFACE) {
              notNullableParams[n] = true;
            }
            else {
              Set<CombinedData.ParamKey> npKeys = parameterFlow[n];
              if (npKeys == null) {
                npKeys = new HashSet<CombinedData.ParamKey>();
                parameterFlow[n] = npKeys;
              }
              npKeys.add(new CombinedData.ParamKey(method, i - shift, stable));
            }
          }
        }
        BasicValue receiver = null;
        if (shift == 1) {
          receiver = values.remove(0);
        }
        boolean thisCall = (opCode == INVOKEINTERFACE || opCode == INVOKEVIRTUAL) && receiver == ThisValue;
        return new CombinedData.TrackableCallValue(origin, retType, method, values, stable, thisCall);
      case MULTIANEWARRAY:
        return new AbstractValues.NotNullValue(super.naryOperation(insn, values).getType());
      default:
    }
    return track(origin, super.naryOperation(insn, values));
  }
}
