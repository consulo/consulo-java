// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.psi.PsiElement;
import consulo.util.collection.FList;

import java.util.List;

public class InsideFinallyTrap implements DfaControlTransferValue.Trap {
  private final PsiElement myFinallyBlock;

  public InsideFinallyTrap(PsiElement block) { 
    myFinallyBlock = block; 
  }

  @Override
  public List<DfaInstructionState> dispatch(DfaMemoryState state,
                                                                   DataFlowInterpreter interpreter,
                                                                   DfaControlTransferValue.TransferTarget target,
                                                                   FList<DfaControlTransferValue.Trap> nextTraps) {
    DfaValue value = state.pop();
    if (!(value instanceof DfaControlTransferValue)) {
      throw new IllegalStateException("Expected control transfer on stack; got " + value);
    }
    return DfaControlTransferValue.dispatch(state, interpreter, target, nextTraps);
  }

  @Override
  public PsiElement getAnchor() {
    return myFinallyBlock;
  }
}
