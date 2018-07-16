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

import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.FalseValue;
import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.TrueValue;
import static com.intellij.codeInspection.bytecodeAnalysis.CombinedData.ThisValue;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.NullableOut;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.Out;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.RETURN;

import java.util.Collections;
import java.util.Set;

import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.MethodNode;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;
import consulo.internal.org.objectweb.asm.tree.analysis.Frame;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph;
import com.intellij.util.SingletonSet;
import com.intellij.util.containers.HashSet;

// specialized class for analyzing methods without branching in single pass
final class CombinedAnalysis {

  private final ControlFlowGraph controlFlow;
  private final Method method;
  private final CombinedInterpreter interpreter;
  private BasicValue returnValue;
  private boolean exception;
  private final MethodNode methodNode;

  CombinedAnalysis(Method method, ControlFlowGraph controlFlow) {
    this.method = method;
    this.controlFlow = controlFlow;
    methodNode = controlFlow.methodNode;
    interpreter = new CombinedInterpreter(methodNode.instructions, Type.getArgumentTypes(methodNode.desc).length);
  }

  final void analyze() throws AnalyzerException
  {
    Frame<BasicValue> frame = createStartFrame();
    int insnIndex = 0;

    while (true) {
      AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
      switch (insnNode.getType()) {
        case AbstractInsnNode.LABEL:
        case AbstractInsnNode.LINE:
        case AbstractInsnNode.FRAME:
          insnIndex = controlFlow.transitions[insnIndex][0];
          break;
        default:
          switch (insnNode.getOpcode()) {
            case ATHROW:
              exception = true;
              return;
            case ARETURN:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
              returnValue = frame.pop();
              return;
            case RETURN:
              // nothing to return
              return;
            default:
              frame.execute(insnNode, interpreter);
              insnIndex = controlFlow.transitions[insnIndex][0];
          }
      }
    }
  }

  final Equation notNullParamEquation(int i, boolean stable) {
    final Key key = new Key(method, new Direction.In(i, Direction.In.NOT_NULL_MASK), stable);
    final Result result;
    if (interpreter.dereferencedParams[i]) {
      result = new Final(Value.NotNull);
    }
    else {
      Set<CombinedData.ParamKey> calls = interpreter.parameterFlow[i];
      if (calls == null || calls.isEmpty()) {
        result = new Final(Value.Top);
      }
      else {
        Set<Key> keys = new HashSet<Key>();
        for (CombinedData.ParamKey pk: calls) {
          keys.add(new Key(pk.method, new Direction.In(pk.i, Direction.In.NOT_NULL_MASK), pk.stable));
        }
        result = new Pending(new SingletonSet<Product>(new Product(Value.Top, keys)));
      }
    }
    return new Equation(key, result);
  }

  final Equation nullableParamEquation(int i, boolean stable) {
    final Key key = new Key(method, new Direction.In(i, Direction.In.NULLABLE_MASK), stable);
    final Result result;
    if (interpreter.dereferencedParams[i] || interpreter.notNullableParams[i] || returnValue instanceof CombinedData.NthParamValue && ((CombinedData.NthParamValue)returnValue).n == i) {
      result = new Final(Value.Top);
    }
    else {
      Set<CombinedData.ParamKey> calls = interpreter.parameterFlow[i];
      if (calls == null || calls.isEmpty()) {
        result = new Final(Value.Null);
      }
      else {
        Set<Product> sum = new HashSet<Product>();
        for (CombinedData.ParamKey pk: calls) {
          sum.add(new Product(Value.Top, Collections.singleton(new Key(pk.method, new Direction.In(pk.i, Direction.In.NULLABLE_MASK), pk.stable))));
        }
        result = new Pending(sum);
      }
    }
    return new Equation(key, result);
  }

  final Equation contractEquation(int i, Value inValue, boolean stable) {
    final Key key = new Key(method, new Direction.InOut(i, inValue), stable);
    final Result result;
    if (exception || (inValue == Value.Null && interpreter.dereferencedParams[i])) {
      result = new Final(Value.Bot);
    }
    else if (FalseValue == returnValue) {
      result = new Final(Value.False);
    }
    else if (TrueValue == returnValue) {
      result = new Final(Value.True);
    }
    else if (returnValue instanceof CombinedData.TrackableNullValue) {
      result = new Final(Value.Null);
    }
    else if (returnValue instanceof AbstractValues.NotNullValue || ThisValue == returnValue) {
      result = new Final(Value.NotNull);
    }
    else if (returnValue instanceof CombinedData.NthParamValue && ((CombinedData.NthParamValue)returnValue).n == i) {
      result = new Final(inValue);
    }
    else if (returnValue instanceof CombinedData.TrackableCallValue) {
      CombinedData.TrackableCallValue call = (CombinedData.TrackableCallValue)returnValue;
      HashSet<Key> keys = new HashSet<Key>();
      for (int argI = 0; argI < call.args.size(); argI++) {
        BasicValue arg = call.args.get(argI);
        if (arg instanceof CombinedData.NthParamValue) {
          CombinedData.NthParamValue npv = (CombinedData.NthParamValue)arg;
          if (npv.n == i) {
            keys.add(new Key(call.method, new Direction.InOut(argI, inValue), call.stableCall));
          }
        }
      }
      if (ASMUtils.isReferenceType(call.getType())) {
        keys.add(new Key(call.method, Out, call.stableCall));
      }
      if (keys.isEmpty()) {
        result = new Final(Value.Top);
      } else {
        result = new Pending(new SingletonSet<Product>(new Product(Value.Top, keys)));
      }
    }
    else {
      result = new Final(Value.Top);
    }
    return new Equation(key, result);
  }

  final Equation outContractEquation(boolean stable) {
    final Key key = new Key(method, Out, stable);
    final Result result;
    if (exception) {
      result = new Final(Value.Bot);
    }
    else if (FalseValue == returnValue) {
      result = new Final(Value.False);
    }
    else if (TrueValue == returnValue) {
      result = new Final(Value.True);
    }
    else if (returnValue instanceof CombinedData.TrackableNullValue) {
      result = new Final(Value.Null);
    }
    else if (returnValue instanceof AbstractValues.NotNullValue || returnValue == ThisValue) {
      result = new Final(Value.NotNull);
    }
    else if (returnValue instanceof CombinedData.TrackableCallValue) {
      CombinedData.TrackableCallValue call = (CombinedData.TrackableCallValue)returnValue;
      Key callKey = new Key(call.method, Out, call.stableCall);
      Set<Key> keys = new SingletonSet<Key>(callKey);
      result = new Pending(new SingletonSet<Product>(new Product(Value.Top, keys)));
    }
    else {
      result = new Final(Value.Top);
    }
    return new Equation(key, result);
  }

  final Equation nullableResultEquation(boolean stable) {
    final Key key = new Key(method, NullableOut, stable);
    final Result result;
    if (exception ||
        returnValue instanceof CombinedData.Trackable && interpreter.dereferencedValues[((CombinedData.Trackable)returnValue).getOriginInsnIndex()]) {
      result = new Final(Value.Bot);
    }
    else if (returnValue instanceof CombinedData.TrackableCallValue) {
      CombinedData.TrackableCallValue call = (CombinedData.TrackableCallValue)returnValue;
      Key callKey = new Key(call.method, NullableOut, call.stableCall || call.thisCall);
      Set<Key> keys = new SingletonSet<Key>(callKey);
      result = new Pending(new SingletonSet<Product>(new Product(Value.Null, keys)));
    }
    else if (returnValue instanceof CombinedData.TrackableNullValue) {
      result = new Final(Value.Null);
    }
    else {
      result = new Final(Value.Bot);
    }
    return new Equation(key, result);
  }

  final Frame<BasicValue> createStartFrame() {
    Frame<BasicValue> frame = new Frame<BasicValue>(methodNode.maxLocals, methodNode.maxStack);
    Type returnType = Type.getReturnType(methodNode.desc);
    BasicValue returnValue = Type.VOID_TYPE.equals(returnType) ? null : new BasicValue(returnType);
    frame.setReturn(returnValue);

    Type[] args = Type.getArgumentTypes(methodNode.desc);
    int local = 0;
    if ((methodNode.access & ACC_STATIC) == 0) {
      frame.setLocal(local++, ThisValue);
    }
    for (int i = 0; i < args.length; i++) {
      BasicValue value = new CombinedData.NthParamValue(args[i], i);
      frame.setLocal(local++, value);
      if (args[i].getSize() == 2) {
        frame.setLocal(local++, BasicValue.UNINITIALIZED_VALUE);
      }
    }
    while (local < methodNode.maxLocals) {
      frame.setLocal(local++, BasicValue.UNINITIALIZED_VALUE);
    }
    return frame;
  }
}
