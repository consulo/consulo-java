// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.dataFlow.value.VariableDescriptor;

import java.util.List;
import java.util.Objects;

public class InstructionTransfer implements DfaControlTransferValue.TransferTarget {
  private final ControlFlow.ControlFlowOffset myOffset;
  private final List<VariableDescriptor> myVarsToFlush;

  public InstructionTransfer(ControlFlow.ControlFlowOffset offset, List<VariableDescriptor> flush) {
    myOffset = offset;
    myVarsToFlush = flush;
  }

  @Override
  public int [] getPossibleTargets() {
    return new int[]{myOffset.getInstructionOffset()};
  }
  
  @Override
  public List<DfaInstructionState> dispatch(DfaMemoryState state,
                                                              DataFlowInterpreter interpreter) {
    state.flushVariables(var -> myVarsToFlush.contains(var.getDescriptor()));
    return List.of(new DfaInstructionState(interpreter.getInstruction(myOffset.getInstructionOffset()), state));
  }

  @Override
  public String toString() {
    return "-> " + myOffset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InstructionTransfer transfer = (InstructionTransfer)o;
    return myOffset.equals(transfer.myOffset) && myVarsToFlush.equals(transfer.myVarsToFlush);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myOffset, myVarsToFlush);
  }
}
