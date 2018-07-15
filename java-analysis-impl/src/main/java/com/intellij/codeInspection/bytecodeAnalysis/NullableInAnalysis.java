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

import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.InstanceOfCheckValue;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.Identity;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.NPE;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.Return;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.combineNullable;
import static org.objectweb.asm.Opcodes.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph;
import com.intellij.codeInspection.bytecodeAnalysis.asm.RichControlFlow;

class NullableInAnalysis extends Analysis<PResults.PResult> {
  final private State[] pending;

  private final NullableInterpreter interpreter = new NullableInterpreter();

  protected NullableInAnalysis(RichControlFlow richControlFlow, Direction direction, boolean stable, State[] pending) {
    super(richControlFlow, direction, stable);
    this.pending = pending;
  }

  @Nonnull
  Equation mkEquation(PResults.PResult result) {
    if (NPE == result) {
      return new Equation(aKey, new Final(Value.Top));
    }
    if (Identity == result || Return == result) {
      return new Equation(aKey, new Final(Value.Null));
    }
    else {
      PResults.ConditionalNPE condNpe = (PResults.ConditionalNPE) result;
      Set<Product> components = new HashSet<Product>();
      for (Set<Key> prod : condNpe.sop) {
        components.add(new Product(Value.Top, prod));
      }
      return new Equation(aKey, new Pending(components));
    }
  }

  private int id;
  private Frame<BasicValue> nextFrame;
  private PResults.PResult myResult = Identity;
  private PResults.PResult subResult = Identity;
  private boolean top;

  @Nonnull
  protected Equation analyze() throws AnalyzerException
  {
    pendingPush(createStartState());
    int steps = 0;
    while (pendingTop > 0 && earlyResult == null) {
      steps ++;
      if (steps >= STEPS_LIMIT) {
        throw new AnalyzerException(null, "limit is reached, steps: " + steps + " in method " + method);
      }
      State state = pending[--pendingTop];
      int insnIndex = state.conf.insnIndex;
      Conf conf = state.conf;
      List<Conf> history = state.history;

      boolean fold = false;
      if (dfsTree.loopEnters[insnIndex]) {
        for (Conf prev : history) {
          if (AbstractValues.isInstance(conf, prev)) {
            fold = true;
            break;
          }
        }
      }
      if (fold) {
        addComputed(insnIndex, state);
      }
      else {
        State baseState = null;
        List<State> thisComputed = computed[insnIndex];
        if (thisComputed != null) {
          for (State prevState : thisComputed) {
            if (stateEquiv(state, prevState)) {
              baseState = prevState;
              break;
            }
          }
        }
        if (baseState == null) {
          processState(state);
        }
      }
    }
    if (earlyResult != null) {
      return mkEquation(earlyResult);
    } else {
      return mkEquation(myResult);
    }
  }

  private void processState(State state) throws AnalyzerException {
    Conf conf = state.conf;
    int insnIndex = conf.insnIndex;
    List<Conf> history = state.history;
    boolean taken = state.taken;
    Frame<BasicValue> frame = conf.frame;
    AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
    List<Conf> nextHistory = dfsTree.loopEnters[insnIndex] ? append(history, conf) : history;

    addComputed(insnIndex, state);
    execute(frame, insnNode, taken);

    if (subResult == NPE || top) {
      earlyResult = NPE;
      return;
    }

    if (subResult instanceof PResults.ConditionalNPE) {
      myResult = combineNullable(myResult, subResult);
    }

    int opcode = insnNode.getOpcode();
    switch (opcode) {
      case ARETURN:
        if (popValue(frame) instanceof AbstractValues.ParamValue) {
          earlyResult = NPE;
        }
        return;
      case IRETURN:
      case LRETURN:
      case FRETURN:
      case DRETURN:
      case RETURN:
        return;
      default:
    }

    if (opcode == ATHROW) {
      if (taken) {
        earlyResult = NPE;
      }
      return;
    }

    if (opcode == IFNONNULL && popValue(frame) instanceof AbstractValues.ParamValue) {
      int nextInsnIndex = insnIndex + 1;
      pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false));
      return;
    }

    if (opcode == IFNULL && popValue(frame) instanceof AbstractValues.ParamValue) {
      int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false));
      return;
    }

    if (opcode == IFEQ && popValue(frame) == InstanceOfCheckValue) {
      int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode)insnNode).label);
      pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false));
      return;
    }

    if (opcode == IFNE && popValue(frame) == InstanceOfCheckValue) {
      int nextInsnIndex = insnIndex + 1;
      pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false));
      return;
    }

    // general case
    for (int nextInsnIndex : controlFlow.transitions[insnIndex]) {
      Frame<BasicValue> nextFrame1 = nextFrame;
      if (controlFlow.errors[nextInsnIndex] && controlFlow.errorTransitions.contains(new ControlFlowGraph.Edge(insnIndex, nextInsnIndex))) {
        nextFrame1 = new Frame<BasicValue>(frame);
        nextFrame1.clearStack();
        nextFrame1.push(ASMUtils.THROWABLE_VALUE);
      }
      pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame1), nextHistory, taken, false));
    }

  }

  private int pendingTop;

  private void pendingPush(State state) throws AnalyzerException {
    if (pendingTop >= STEPS_LIMIT) {
      throw new AnalyzerException(null, "limit is reached in method " + method);
    }
    pending[pendingTop++] = state;
  }

  private void execute(Frame<BasicValue> frame, AbstractInsnNode insnNode, boolean taken) throws AnalyzerException {
    switch (insnNode.getType()) {
      case AbstractInsnNode.LABEL:
      case AbstractInsnNode.LINE:
      case AbstractInsnNode.FRAME:
        nextFrame = frame;
        subResult = Identity;
        top = false;
        break;
      default:
        nextFrame = new Frame<BasicValue>(frame);
        interpreter.reset(taken);
        nextFrame.execute(insnNode, interpreter);
        subResult = interpreter.getSubResult();
        top = interpreter.top;
    }
  }
}
