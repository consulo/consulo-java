// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.psi.PsiElement;
import consulo.util.collection.FList;

import java.util.List;

public class TryCatchAllTrap implements DfaControlTransferValue.Trap {
  private final PsiElement myAnchor;
  private final ControlFlow.ControlFlowOffset myTarget;

  public TryCatchAllTrap(PsiElement anchor, ControlFlow.ControlFlowOffset target) {
    myAnchor = anchor;
    myTarget = target;
  }

  @Override
  public List<DfaInstructionState> dispatch(DfaMemoryState state,
                                                                   DataFlowInterpreter interpreter,
                                                                   DfaControlTransferValue.TransferTarget target,
                                                                   FList<DfaControlTransferValue.Trap> nextTraps) {
    if (!(target instanceof ExceptionTransfer)) {
      return DfaControlTransferValue.dispatch(state, interpreter, target, nextTraps);
    }
    state.emptyStack();
    return List.of(new DfaInstructionState(interpreter.getInstruction(myTarget.getInstructionOffset()), state));
  }

  @Override
  public PsiElement getAnchor() {
    return myAnchor;
  }

  @Override
  public String toString() {
    return "TryCatchAll -> " + myTarget;
  }
}
