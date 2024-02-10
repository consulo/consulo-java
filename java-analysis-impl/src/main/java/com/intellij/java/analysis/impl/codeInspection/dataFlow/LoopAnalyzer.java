// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.ConditionalGotoInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.GotoInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.Instruction;
import consulo.component.util.graph.DFSTBuilder;
import consulo.component.util.graph.Graph;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;

import jakarta.annotation.Nonnull;
import java.util.*;
import java.util.function.IntConsumer;

class LoopAnalyzer {
  private static class MyGraph implements Graph<Instruction> {
    @Nonnull
    private final ControlFlow myFlow;
    private final Instruction[] myInstructions;
    private final IntObjectMap<int[]> myIns = IntMaps.newIntObjectHashMap();

    private MyGraph(@Nonnull ControlFlow flow) {
      myFlow = flow;
      myInstructions = flow.getInstructions();
      for (Instruction instruction : myInstructions) {
        int fromIndex = instruction.getIndex();
        int[] to = getSuccessorIndices(fromIndex, myInstructions);
        for (int toIndex : to) {
          int[] froms = myIns.get(toIndex);
          if (froms == null) {
            froms = new int[]{fromIndex};
          } else {
            froms = ArrayUtil.append(froms, fromIndex);
          }
          myIns.put(toIndex, froms);
        }
      }
    }

    @Nonnull
    @Override
    public Collection<Instruction> getNodes() {
      return Arrays.asList(myFlow.getInstructions());
    }

    @Nonnull
    @Override
    public Iterator<Instruction> getIn(Instruction n) {
      int[] ins = myIns.get(n.getIndex());
      return indicesToInstructions(ins);
    }

    @Nonnull
    @Override
    public Iterator<Instruction> getOut(Instruction instruction) {
      int fromIndex = instruction.getIndex();
      int[] next = getSuccessorIndices(fromIndex, myInstructions);
      return indicesToInstructions(next);
    }

    @Nonnull
    private Iterator<Instruction> indicesToInstructions(int[] next) {
      if (next == null) {
        return Collections.emptyIterator();
      }
      List<Instruction> out = new ArrayList<>(next.length);
      for (int i : next) {
        out.add(myInstructions[i]);
      }
      return out.iterator();
    }
  }


  static int[] calcInLoop(ControlFlow controlFlow) {
    final int[] loop = new int[controlFlow.getInstructionCount()]; // loop[i] = loop number(strongly connected component number) of i-th instruction or 0 if outside loop

    MyGraph graph = new MyGraph(controlFlow);
    final DFSTBuilder<Instruction> builder = new DFSTBuilder<>(graph);
    IntList sccs = builder.getSCCs();
    sccs.forEach(new IntConsumer() {
      private int myTNumber;
      private int component;

      @Override
      public void accept(int size) {
        int value = size > 1 ? ++component : 0;
        for (int i = 0; i < size; i++) {
          Instruction instruction = builder.getNodeByTNumber(myTNumber + i);
          loop[instruction.getIndex()] = value;
        }
        myTNumber += size;
      }
    });

    return loop;
  }

  @Nonnull
  static int[] getSuccessorIndices(int i, Instruction[] myInstructions) {
    Instruction instruction = myInstructions[i];
    if (instruction instanceof GotoInstruction) {
      return new int[]{((GotoInstruction) instruction).getOffset()};
    }
    if (instruction instanceof ControlTransferInstruction) {
      return ArrayUtil.toIntArray(((ControlTransferInstruction) instruction).getPossibleTargetIndices());
    }
    if (instruction instanceof ConditionalGotoInstruction) {
      int offset = ((ConditionalGotoInstruction) instruction).getOffset();
      if (offset != i + 1) {
        return new int[]{
            i + 1,
            offset
        };
      }
    }
    return i == myInstructions.length - 1 ? ArrayUtil.EMPTY_INT_ARRAY : new int[]{i + 1};
  }


}
