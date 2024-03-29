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
package com.intellij.java.analysis.impl.psi.controlFlow;

import com.intellij.java.language.impl.psi.controlFlow.*;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.progress.ProgressManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.util.collection.Stack;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.util.lang.ExceptionUtil;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author max
 * Date: Mar 22, 2002
 */
public class DefUseUtil {
  private static final Logger LOG = Logger.getInstance(DefUseUtil.class);

  private DefUseUtil() {
  }

  public static class Info {
    private final PsiVariable myVariable;
    private final PsiElement myContext;
    private final boolean myIsRead;

    public Info(PsiVariable variable, PsiElement context, boolean read) {
      myVariable = variable;
      myContext = context;
      myIsRead = read;
    }

    public PsiVariable getVariable() {
      return myVariable;
    }

    public PsiElement getContext() {
      return myContext;
    }

    public boolean isRead() {
      return myIsRead;
    }
  }

  private static class InstructionState implements Comparable<InstructionState> {
    private Set<PsiVariable> myUsed;
    private final InstructionKey myInstructionKey;
    private final List<InstructionKey> myBackwardTraces;
    private boolean myIsVisited;

    public InstructionState(@Nonnull InstructionKey instructionKey) {
      myInstructionKey = instructionKey;
      myBackwardTraces = new ArrayList<>(2);
      myUsed = null;
    }

    public void addBackwardTrace(InstructionKey key) {
      myBackwardTraces.add(key);
    }

    public List<InstructionKey> getBackwardTraces() {
      return myBackwardTraces;
    }

    public InstructionKey getInstructionKey() {
      return myInstructionKey;
    }

    void addUsed(PsiVariable psiVariable) {
      touch();
      myUsed.add(psiVariable);
    }

    boolean removeUsed(PsiVariable psiVariable) {
      touch();
      return myUsed.remove(psiVariable);
    }

    private void touch() {
      if (myUsed == null) {
        myUsed = new HashSet<>();
      }
    }

    public void addUsedFrom(InstructionState state) {
      touch();
      myUsed.addAll(state.myUsed);
    }

    public boolean contains(InstructionState state) {
      return myUsed != null && state.myUsed != null && myUsed.containsAll(state.myUsed);
    }

    public void markVisited() {
      myIsVisited = true;
    }

    public boolean isVisited() {
      return myIsVisited;
    }

    @Override
    public int compareTo(@Nonnull InstructionState other) {
      return myInstructionKey.compareTo(other.myInstructionKey);
    }

    @Override
    public String toString() {
      return myInstructionKey + " " + myBackwardTraces + (myIsVisited ? "(v)" : "(n)") + " " + (myUsed != null ? myUsed : "-");
    }
  }

  @Nullable
  public static List<Info> getUnusedDefs(PsiCodeBlock body, Set<PsiVariable> outUsedVariables) {
    if (body == null) {
      return null;
    }

    ControlFlow flow;
    try {
      flow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, ourPolicy);
    } catch (AnalysisCanceledException e) {
      return null;
    }
    List<Instruction> instructions = flow.getInstructions();
    if (LOG.isDebugEnabled()) {
      LOG.debug(flow.toString());
    }

    Set<PsiVariable> assignedVariables = new HashSet<>();
    Set<PsiVariable> readVariables = new HashSet<>();
    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      ProgressManager.checkCanceled();
      if (instruction instanceof WriteVariableInstruction) {
        WriteVariableInstruction writeInstruction = (WriteVariableInstruction) instruction;
        PsiElement context = flow.getElement(i);
        context = PsiTreeUtil.getParentOfType(context, PsiStatement.class, false);
        PsiVariable psiVariable = writeInstruction.variable;
        if (context != null && !(context instanceof PsiDeclarationStatement && psiVariable.getInitializer() == null)) {
          assignedVariables.add(psiVariable);
        }
      } else if (instruction instanceof ReadVariableInstruction) {
        ReadVariableInstruction readInstruction = (ReadVariableInstruction) instruction;
        readVariables.add(readInstruction.variable);
      }
    }

    Map<InstructionKey, InstructionState> stateMap;
    try {
      stateMap = InstructionStateWalker.getStates(instructions);
    } catch (InstructionKey.OverflowException e) {
      LOG.error("Failed to compute paths in the control flow graph", e, flow.toString());
      return null;
    }
    InstructionState[] states = stateMap.values().toArray(new InstructionState[0]);
    Arrays.sort(states);

    BitSet usefulWrites = new BitSet(instructions.size());

    Deque<InstructionState> queue = new ArrayDeque<>(8);

    for (int i = states.length - 1; i >= 0; i--) {
      final InstructionState outerState = states[i];
      if (outerState.isVisited()) {
        continue;
      }
      outerState.touch();

      for (PsiVariable psiVariable : assignedVariables) {
        if (psiVariable instanceof PsiField) {
          outerState.addUsed(psiVariable);
        }
      }
      queue.addLast(outerState);

      while (!queue.isEmpty()) {
        ProgressManager.checkCanceled();
        InstructionState state = queue.removeFirst();
        state.markVisited();

        InstructionKey key = state.getInstructionKey();
        if (key.getOffset() < instructions.size()) {
          Instruction instruction = instructions.get(key.getOffset());

          if (instruction instanceof WriteVariableInstruction) {
            WriteVariableInstruction writeInstruction = (WriteVariableInstruction) instruction;
            PsiVariable psiVariable = writeInstruction.variable;
            outUsedVariables.add(psiVariable);
            if (state.removeUsed(psiVariable)) {
              usefulWrites.set(key.getOffset());
            }
          } else if (instruction instanceof ReadVariableInstruction) {
            ReadVariableInstruction readInstruction = (ReadVariableInstruction) instruction;
            state.addUsed(readInstruction.variable);
            outUsedVariables.add(readInstruction.variable);
          } else {
            state.touch();
          }
        }

        List<InstructionKey> backwardTraces = state.getBackwardTraces();
        for (InstructionKey prevKeys : backwardTraces) {
          InstructionState prevState = stateMap.get(prevKeys);
          if (prevState != null && !prevState.contains(state)) {
            prevState.addUsedFrom(state);
            queue.addLast(prevState);
          }
        }
      }
    }

    List<Info> unusedDefs = new ArrayList<>();

    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      if (instruction instanceof WriteVariableInstruction) {
        WriteVariableInstruction writeInstruction = (WriteVariableInstruction) instruction;
        if (!usefulWrites.get(i)) {
          PsiElement context = PsiTreeUtil.getNonStrictParentOfType(flow.getElement(i), PsiStatement.class, PsiAssignmentExpression.class, PsiPostfixExpression.class, PsiPrefixExpression
              .class);
          PsiVariable psiVariable = writeInstruction.variable;
          if (context != null && !(context instanceof PsiTryStatement)) {
            if (context instanceof PsiDeclarationStatement && psiVariable.getInitializer() == null) {
              if (!assignedVariables.contains(psiVariable)) {
                unusedDefs.add(new Info(psiVariable, context, false));
              }
            } else {
              unusedDefs.add(new Info(psiVariable, context, readVariables.contains(psiVariable)));
            }
          }
        }
      }
    }

    return unusedDefs;
  }

  @Nonnull
  public static PsiElement[] getDefs(PsiCodeBlock body, final PsiVariable def, PsiElement ref) {
    return getDefs(body, def, ref, false);
  }

  @Nonnull
  public static PsiElement[] getDefs(PsiCodeBlock body, final PsiVariable def, PsiElement ref, boolean rethrow) {
    try {
      RefsDefs refsDefs = new RefsDefs(body) {
        private final IntList[] myBackwardTraces = getBackwardTraces(instructions);

        @Override
        protected int nNext(int index) {
          return myBackwardTraces[index].size();
        }

        @Override
        protected int getNext(int index, int no) {
          return myBackwardTraces[index].get(no);
        }

        @Override
        protected boolean defs() {
          return true;
        }

        @Override
        protected void processInstruction(final Set<PsiElement> res, final Instruction instruction, int index) {
          if (instruction instanceof WriteVariableInstruction) {
            WriteVariableInstruction instructionW = (WriteVariableInstruction) instruction;
            if (instructionW.variable == def) {

              final PsiElement element = flow.getElement(index);
              element.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitReferenceExpression(PsiReferenceExpression ref) {
                  if (PsiUtil.isAccessedForWriting(ref)) {
                    if (ref.resolve() == def) {
                      res.add(ref);
                    }
                  }
                }

                @Override
                public void visitVariable(PsiVariable var) {
                  if (var == def && (var instanceof PsiParameter || var.hasInitializer())) {
                    res.add(var);
                  }
                }
              });
            }
          }
        }
      };
      return refsDefs.get(def, ref);
    } catch (AnalysisCanceledException e) {
      if (rethrow) {
        ExceptionUtil.rethrowAllAsUnchecked(e);
      }
      return PsiElement.EMPTY_ARRAY;
    }
  }

  @Nonnull
  public static PsiElement[] getRefs(PsiCodeBlock body, final PsiVariable def, PsiElement ref) {
    try {
      RefsDefs refsDefs = new RefsDefs(body) {
        @Override
        protected int nNext(int index) {
          return instructions.get(index).nNext();
        }

        @Override
        protected int getNext(int index, int no) {
          return instructions.get(index).getNext(index, no);
        }

        @Override
        protected boolean defs() {
          return false;
        }

        @Override
        protected void processInstruction(final Set<PsiElement> res, final Instruction instruction, int index) {
          if (instruction instanceof ReadVariableInstruction) {
            ReadVariableInstruction instructionR = (ReadVariableInstruction) instruction;
            if (instructionR.variable == def) {

              final PsiElement element = flow.getElement(index);
              element.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitReferenceExpression(PsiReferenceExpression ref) {
                  if (ref.resolve() == def) {
                    res.add(ref);
                  }
                }
              });
            }
          }
        }
      };
      return refsDefs.get(def, ref);
    } catch (AnalysisCanceledException e) {
      return PsiElement.EMPTY_ARRAY;
    }
  }

  private abstract static class RefsDefs {
    protected abstract int nNext(int index);

    protected abstract int getNext(int index, int no);

    final List<Instruction> instructions;
    final ControlFlow flow;
    final PsiCodeBlock body;


    protected RefsDefs(PsiCodeBlock body) throws AnalysisCanceledException {
      this.body = body;
      flow = ControlFlowFactory.getInstance(body.getProject()).getControlFlow(body, ourPolicy);
      instructions = flow.getInstructions();
    }

    protected abstract void processInstruction(Set<PsiElement> res, final Instruction instruction, int index);

    protected abstract boolean defs();

    @Nonnull
    private PsiElement[] get(final PsiVariable def, PsiElement refOrDef) {
      if (body == null) {
        return PsiElement.EMPTY_ARRAY;
      }

      final boolean[] visited = new boolean[instructions.size() + 1];
      visited[visited.length - 1] = true; // stop on the code end
      int elem = defs() ? flow.getStartOffset(refOrDef) : flow.getEndOffset(refOrDef);

      if (elem != -1) {
        if (!defs() && instructions.get(elem) instanceof ReadVariableInstruction) {
          LOG.assertTrue(nNext(elem) == 1);
          LOG.assertTrue(getNext(elem, 0) == elem + 1);
          elem += 1;
        }

        final Set<PsiElement> res = new HashSet<>();
        class Inner {

          void traverse(int index) {
            visited[index] = true;

            if (defs()) {
              final Instruction instruction = instructions.get(index);
              processInstruction(res, instruction, index);
              if (instruction instanceof WriteVariableInstruction) {
                WriteVariableInstruction instructionW = (WriteVariableInstruction) instruction;
                if (instructionW.variable == def) {
                  return;
                }
              }

              // hack: ControlFlow doesn't contains parameters initialization
              if (index == 0 && def instanceof PsiParameter) {
                res.add(def.getNameIdentifier());
              }
            }

            final int nNext = nNext(index);
            for (int i = 0; i < nNext; i++) {
              final int prev = getNext(index, i);
              if (!visited[prev]) {
                if (!defs()) {
                  final Instruction instruction = instructions.get(prev);
                  if (instruction instanceof WriteVariableInstruction) {
                    WriteVariableInstruction instructionW = (WriteVariableInstruction) instruction;
                    if (instructionW.variable == def) {
                      continue;
                    }
                  } else {
                    processInstruction(res, instruction, prev);
                  }
                }
                traverse(prev);

              }
            }
          }
        }
        new Inner().traverse(elem);
        return PsiUtilCore.toPsiElementArray(res);
      }
      return PsiElement.EMPTY_ARRAY;
    }
  }


  @Nonnull
  private static IntList[] getBackwardTraces(final List<Instruction> instructions) {
    final IntList[] states = new IntList[instructions.size()];
    for (int i = 0; i < states.length; i++) {
      states[i] = IntLists.newArrayList();
    }

    for (int i = 0; i < instructions.size(); i++) {
      final Instruction instruction = instructions.get(i);
      for (int j = 0; j != instruction.nNext(); ++j) {
        final int next = instruction.getNext(i, j);
        if (next < states.length) {
          states[next].add(i);
        }
      }
    }
    return states;
  }

  private static class WalkThroughStack {
    private final Stack<InstructionKey> myFrom;
    private final Stack<InstructionKey> myNext;

    WalkThroughStack(int size) {
      if (size < 2) {
        size = 2;
      }
      myFrom = new Stack<>(size);
      myNext = new Stack<>(size);
    }

    void push(InstructionKey fromKey, InstructionKey nextKey) {
      myFrom.push(fromKey);
      myNext.push(nextKey);
    }

    InstructionKey peekFrom() {
      return myFrom.peek();
    }

    InstructionKey popNext() {
      myFrom.pop();
      return myNext.pop();
    }

    boolean isEmpty() {
      return myFrom.isEmpty();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0, limit = Math.min(myFrom.size(), myNext.size()); i < limit; i++) {
        if (sb.length() != 0) {
          sb.append(", ");
        }
        sb.append(myFrom.get(i)).append("->").append(myNext.get(i));
      }
      return sb.toString();
    }
  }

  private static class InstructionStateWalker {
    private final Map<InstructionKey, InstructionState> myStates;
    private final WalkThroughStack myWalkThroughStack;
    private final List<Instruction> myInstructions;

    private InstructionStateWalker(List<Instruction> instructions) {
      myStates = new HashMap<>(instructions.size());
      myWalkThroughStack = new WalkThroughStack(instructions.size() / 2);
      myInstructions = instructions;
    }

    private Map<InstructionKey, InstructionState> walk() {
      InstructionKey startKey = InstructionKey.create(0);
      myStates.put(startKey, new InstructionState(startKey));
      myWalkThroughStack.push(InstructionKey.create(-1), startKey);

      InstructionKeySet visited = new InstructionKeySet(myInstructions.size() + 1);
      while (!myWalkThroughStack.isEmpty()) {
        ProgressManager.checkCanceled();
        InstructionKey fromKey = myWalkThroughStack.peekFrom();
        InstructionKey nextKey = myWalkThroughStack.popNext();
        addBackwardTrace(fromKey, nextKey);
        if (!visited.contains(nextKey)) {
          visit(nextKey);
          visited.add(nextKey);
        }
      }
      return myStates;
    }

    private void visit(InstructionKey fromKey) {
      if (fromKey.getOffset() >= myInstructions.size()) {
        return;
      }
      final Instruction instruction = myInstructions.get(fromKey.getOffset());
      if (instruction instanceof CallInstruction) {
        int nextOffset = ((CallInstruction) instruction).offset;
        LOG.assertTrue(nextOffset != 0);
        int returnOffset = fromKey.getOffset() + 1;
        InstructionKey nextKey = fromKey.push(nextOffset, returnOffset);
        myWalkThroughStack.push(fromKey, nextKey);
      } else if (instruction instanceof ReturnInstruction) {
        int overriddenOffset = ((ReturnInstruction) instruction).offset;
        InstructionKey nextKey = fromKey.pop(overriddenOffset);
        myWalkThroughStack.push(fromKey, nextKey);
      } else {
        for (int no = 0; no != instruction.nNext(); no++) {
          final int nextOffset = instruction.getNext(fromKey.getOffset(), no);
          InstructionKey nextKey = fromKey.next(nextOffset);
          myWalkThroughStack.push(fromKey, nextKey);
        }
      }
    }

    private void addBackwardTrace(InstructionKey fromKey, InstructionKey nextKey) {
      if (fromKey.getOffset() >= 0 && nextKey.getOffset() < myInstructions.size()) {
        InstructionState state = myStates.get(nextKey);
        if (state == null) {
          myStates.put(nextKey, state = new InstructionState(nextKey));
        }
        state.addBackwardTrace(fromKey);
      }
    }

    static Map<InstructionKey, InstructionState> getStates(final List<Instruction> instructions) {
      return new InstructionStateWalker(instructions).walk();
    }
  }

  private static final ControlFlowPolicy ourPolicy = new ControlFlowPolicy() {
    @Override
    public PsiVariable getUsedVariable(@Nonnull PsiReferenceExpression refExpr) {
      if (refExpr.isQualified()) {
        return null;
      }

      PsiElement refElement = refExpr.resolve();
      if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
        return (PsiVariable) refElement;
      }

      return null;
    }

    @Override
    public boolean isParameterAccepted(@Nonnull PsiParameter psiParameter) {
      return true;
    }

    @Override
    public boolean isLocalVariableAccepted(@Nonnull PsiLocalVariable psiVariable) {
      return true;
    }
  };
}
