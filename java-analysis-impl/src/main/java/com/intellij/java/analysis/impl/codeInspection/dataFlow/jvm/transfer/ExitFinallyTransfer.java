// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;

import java.util.List;
import java.util.Objects;

// ExitFinallyTransfer formally depends on enterFinally that has backLinks which are instructions bound to the DfaValueFactory
// however, we actually use only instruction offsets from there, and binding to another factory does not change the offsets.
public class ExitFinallyTransfer implements DfaControlTransferValue.TransferTarget {
  private final EnterFinallyTrap myEnterFinally;

  public ExitFinallyTransfer(EnterFinallyTrap enterFinally) { 
    myEnterFinally = enterFinally; 
  }

  @Override
  public int [] getPossibleTargets() {
    return StreamEx.of(myEnterFinally.backLinks()).flatMapToInt(link -> IntStreamEx.of(link.getPossibleTargetIndices()))
      .filter(index -> index != myEnterFinally.getJumpOffset()).toArray();
  }

  @Override
  public List<DfaInstructionState> dispatch(DfaMemoryState state,
                                                                   DataFlowInterpreter interpreter) {
    return ((DfaControlTransferValue)state.pop()).dispatch(state, interpreter);
  }

  @Override
  public String toString() {
    return "ExitFinally";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExitFinallyTransfer transfer = (ExitFinallyTransfer)o;
    return myEnterFinally.equals(transfer.myEnterFinally);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myEnterFinally);
  }
}
