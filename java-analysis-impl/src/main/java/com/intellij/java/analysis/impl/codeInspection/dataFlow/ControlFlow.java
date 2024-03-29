// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.*;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiVariable;
import consulo.util.collection.FList;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import one.util.streamex.StreamEx;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ControlFlow {
  private final List<Instruction> myInstructions = new ArrayList<>();
  private final ObjectIntMap<PsiElement> myElementToStartOffsetMap = ObjectMaps.newObjectIntHashMap();
  private final ObjectIntMap<PsiElement> myElementToEndOffsetMap = ObjectMaps.newObjectIntHashMap();
  private final DfaValueFactory myFactory;
  private int[] myLoopNumbers;

  public ControlFlow(final DfaValueFactory factory) {
    myFactory = factory;
  }

  public Instruction[] getInstructions() {
    return myInstructions.toArray(new Instruction[0]);
  }

  public Instruction getInstruction(int index) {
    return myInstructions.get(index);
  }

  public int getInstructionCount() {
    return myInstructions.size();
  }

  public ControlFlowOffset getNextOffset() {
    return new FixedOffset(myInstructions.size());
  }

  public void startElement(PsiElement psiElement) {
    myElementToStartOffsetMap.putInt(psiElement, myInstructions.size());
  }

  public void finishElement(PsiElement psiElement) {
    myElementToEndOffsetMap.putInt(psiElement, myInstructions.size());
  }

  public void addInstruction(Instruction instruction) {
    instruction.setIndex(myInstructions.size());
    myInstructions.add(instruction);
  }

  int[] getLoopNumbers() {
    return myLoopNumbers;
  }

  void finish() {
    addInstruction(new ReturnInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, FList.emptyList()), null));

    myLoopNumbers = LoopAnalyzer.calcInLoop(this);
    for (int i = 0; i < myInstructions.size(); i++) {
      if (myLoopNumbers[i] > 0 && myInstructions.get(i) instanceof BinopInstruction) {
        ((BinopInstruction) myInstructions.get(i)).widenOperationInLoop();
      }
    }
  }

  public void removeVariable(@Nullable PsiVariable variable) {
    if (variable == null) {
      return;
    }
    addInstruction(new FlushVariableInstruction(myFactory.getVarFactory().createVariableValue(variable)));
  }

  /**
   * @return stream of all accessed variables within this flow
   */
  public Stream<DfaVariableValue> accessedVariables() {
    return StreamEx.of(myInstructions).select(PushInstruction.class)
        .remove(PushInstruction::isReferenceWrite)
        .map(PushInstruction::getValue)
        .select(DfaVariableValue.class).distinct();
  }

  public ControlFlowOffset getStartOffset(final PsiElement element) {
    return new ControlFlowOffset() {
      @Override
      public int getInstructionOffset() {
        return myElementToStartOffsetMap.getInt(element);
      }
    };
  }

  public ControlFlowOffset getEndOffset(final PsiElement element) {
    return new ControlFlowOffset() {
      @Override
      public int getInstructionOffset() {
        return myElementToEndOffsetMap.getInt(element);
      }
    };
  }

  public String toString() {
    StringBuilder result = new StringBuilder();
    final List<Instruction> instructions = myInstructions;

    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      result.append(i).append(": ").append(instruction.toString());
      result.append("\n");
    }
    return result.toString();
  }

  void makeNop(int index) {
    SpliceInstruction instruction = new SpliceInstruction(0);
    instruction.setIndex(index);
    myInstructions.set(index, instruction);
  }

  public abstract static class ControlFlowOffset {
    public abstract int getInstructionOffset();

    @Override
    public String toString() {
      return String.valueOf(getInstructionOffset());
    }
  }

  public static class FixedOffset extends ControlFlowOffset {
    private final int myOffset;

    public FixedOffset(int offset) {
      myOffset = offset;
    }

    @Override
    public int getInstructionOffset() {
      return myOffset;
    }
  }

  public static class DeferredOffset extends ControlFlowOffset {
    private int myOffset = -1;

    @Override
    public int getInstructionOffset() {
      if (myOffset == -1) {
        throw new IllegalStateException("Not set");
      }
      return myOffset;
    }

    public void setOffset(int offset) {
      if (myOffset != -1) {
        throw new IllegalStateException("Already set");
      } else {
        myOffset = offset;
      }
    }

    @Override
    public String toString() {
      return myOffset == -1 ? "<not set>" : super.toString();
    }
  }
}