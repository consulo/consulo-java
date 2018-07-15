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
package com.intellij.codeInspection.bytecodeAnalysis;

import static com.intellij.codeInspection.bytecodeAnalysis.Direction.In;
import static com.intellij.codeInspection.bytecodeAnalysis.Direction.InOut;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph;
import com.intellij.codeInspection.bytecodeAnalysis.asm.DFSTree;
import com.intellij.codeInspection.bytecodeAnalysis.asm.RichControlFlow;

abstract class Analysis<Res> {

  public static final int STEPS_LIMIT = 30000;
  public static final int EQUATION_SIZE_LIMIT = 30;

  final RichControlFlow richControlFlow;
  final Direction direction;
  final ControlFlowGraph controlFlow;
  final MethodNode methodNode;
  final Method method;
  final DFSTree dfsTree;

  final protected List<State>[] computed;
  final Key aKey;

  Res earlyResult;

  protected Analysis(RichControlFlow richControlFlow, Direction direction, boolean stable) {
    this.richControlFlow = richControlFlow;
    this.direction = direction;
    controlFlow = richControlFlow.controlFlow;
    methodNode = controlFlow.methodNode;
    method = new Method(controlFlow.className, methodNode.name, methodNode.desc);
    dfsTree = richControlFlow.dfsTree;
    aKey = new Key(method, direction, stable);
    computed = (List<State>[]) new List[controlFlow.transitions.length];
  }

  final State createStartState() {
    return new State(0, new Conf(0, createStartFrame()), new ArrayList<Conf>(), false, false);
  }

  static boolean stateEquiv(State curr, State prev) {
    if (curr.taken != prev.taken) {
      return false;
    }
    if (curr.conf.fastHashCode != prev.conf.fastHashCode) {
      return false;
    }
    if (!AbstractValues.equiv(curr.conf, prev.conf)) {
      return false;
    }
    if (curr.history.size() != prev.history.size()) {
      return false;
    }
    for (int i = 0; i < curr.history.size(); i++) {
      Conf curr1 = curr.history.get(i);
      Conf prev1 = prev.history.get(i);
      if (curr1.fastHashCode != prev1.fastHashCode || !AbstractValues.equiv(curr1, prev1)) {
        return false;
      }
    }
    return true;
  }

  @Nonnull
  protected abstract Equation analyze() throws AnalyzerException;

  final Frame<BasicValue> createStartFrame() {
    Frame<BasicValue> frame = new Frame<BasicValue>(methodNode.maxLocals, methodNode.maxStack);
    Type returnType = Type.getReturnType(methodNode.desc);
    BasicValue returnValue = Type.VOID_TYPE.equals(returnType) ? null : new BasicValue(returnType);
    frame.setReturn(returnValue);

    Type[] args = Type.getArgumentTypes(methodNode.desc);
    int local = 0;
    if ((methodNode.access & Opcodes.ACC_STATIC) == 0) {
      frame.setLocal(local++, new AbstractValues.NotNullValue(Type.getObjectType(controlFlow.className)));
    }
    for (int i = 0; i < args.length; i++) {
      BasicValue value;
      if (direction instanceof InOut && ((InOut)direction).paramIndex == i ||
          direction instanceof In && ((In)direction).paramIndex == i) {
        value = new AbstractValues.ParamValue(args[i]);
      }
      else {
        value = new BasicValue(args[i]);
      }
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

  static BasicValue popValue(Frame<BasicValue> frame) {
    return frame.getStack(frame.getStackSize() - 1);
  }

  static <A> List<A> append(List<A> xs, A x) {
    ArrayList<A> result = new ArrayList<A>();
    if (xs != null) {
      result.addAll(xs);
    }
    result.add(x);
    return result;
  }

  protected void addComputed(int i, State s) {
    List<State> states = computed[i];
    if (states == null) {
      states = new ArrayList<State>();
      computed[i] = states;
    }
    states.add(s);
  }
}
