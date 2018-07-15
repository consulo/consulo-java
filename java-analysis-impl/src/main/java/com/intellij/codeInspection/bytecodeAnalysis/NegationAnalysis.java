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
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IRETURN;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph;
import com.intellij.util.SingletonSet;
import com.intellij.util.containers.HashSet;

final class NegationAnalysis {

  private final ControlFlowGraph controlFlow;
  private final Method method;
  private final NegationInterpreter interpreter;
  private final MethodNode methodNode;

  private CombinedData.TrackableCallValue conditionValue;
  private BasicValue trueBranchValue;
  private BasicValue falseBranchValue;

  NegationAnalysis(Method method, ControlFlowGraph controlFlow) {
    this.method = method;
    this.controlFlow = controlFlow;
    methodNode = controlFlow.methodNode;
    interpreter = new NegationInterpreter(methodNode.instructions);
  }

  private static void checkAssertion(boolean assertion) throws NegationAnalysisFailure {
    if (!assertion) {
      throw new NegationAnalysisFailure();
    }
  }

  final void analyze() throws AnalyzerException, NegationAnalysisFailure {
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
            case IFEQ:
            case IFNE:
              BasicValue conValue = popValue(frame);
              checkAssertion(conValue instanceof CombinedData.TrackableCallValue);
              frame.execute(insnNode, interpreter);
              conditionValue = (CombinedData.TrackableCallValue)conValue;
              int jumpIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
              int nextIndex = insnIndex + 1;
              proceedBranch(frame, jumpIndex, IFNE == insnNode.getOpcode());
              proceedBranch(frame, nextIndex, IFEQ == insnNode.getOpcode());
              checkAssertion(FalseValue == trueBranchValue);
              checkAssertion(TrueValue == falseBranchValue);
              return;
            default:
              frame.execute(insnNode, interpreter);
              insnIndex = controlFlow.transitions[insnIndex][0];
          }
      }
    }
  }

  private void proceedBranch(Frame<BasicValue> startFrame, int startIndex, boolean branchValue)
    throws NegationAnalysisFailure, AnalyzerException {

    Frame<BasicValue> frame = new Frame<BasicValue>(startFrame);
    int insnIndex = startIndex;

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
            case IRETURN:
              BasicValue returnValue = frame.pop();
              if (branchValue) {
                trueBranchValue = returnValue;
              }
              else {
                falseBranchValue = returnValue;
              }
              return;
            default:
              checkAssertion(controlFlow.transitions[insnIndex].length == 1);
              frame.execute(insnNode, interpreter);
              insnIndex = controlFlow.transitions[insnIndex][0];
          }
      }
    }
  }

  final Equation contractEquation(int i, Value inValue, boolean stable) {
    final Key key = new Key(method, new Direction.InOut(i, inValue), stable);
    final Result result;
    HashSet<Key> keys = new HashSet<Key>();
    for (int argI = 0; argI < conditionValue.args.size(); argI++) {
      BasicValue arg = conditionValue.args.get(argI);
      if (arg instanceof CombinedData.NthParamValue) {
        CombinedData.NthParamValue npv = (CombinedData.NthParamValue)arg;
        if (npv.n == i) {
          keys.add(new Key(conditionValue.method, new Direction.InOut(argI, inValue), conditionValue.stableCall, true));
        }
      }
    }
    if (keys.isEmpty()) {
      result = new Final(Value.Top);
    } else {
      result = new Pending(new SingletonSet<Product>(new Product(Value.Top, keys)));
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

  private static BasicValue popValue(Frame<BasicValue> frame) {
    return frame.getStack(frame.getStackSize() - 1);
  }
}
