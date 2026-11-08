// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiResourceList;
import consulo.util.collection.FList;

import java.util.ArrayList;
import java.util.List;

public class EnterFinallyTrap implements DfaControlTransferValue.Trap {
  private final PsiElement myAnchor;
  private final ControlFlow.ControlFlowOffset myJumpOffset;
  private final List<DfaControlTransferValue> myBackLinks = new ArrayList<>();

  public EnterFinallyTrap(PsiElement anchor, ControlFlow.ControlFlowOffset offset) {
    myAnchor = anchor;
    myJumpOffset = offset; 
  }

  List<DfaControlTransferValue> backLinks() {
    return myBackLinks;
  }

  @Override
  public void link(DfaControlTransferValue instruction) {
    myBackLinks.add(instruction);
  }

  @Override
  public List<DfaInstructionState> dispatch(DfaMemoryState state,
                                                                   DataFlowInterpreter interpreter,
                                                                   DfaControlTransferValue.TransferTarget target,
                                                                   FList<DfaControlTransferValue.Trap> nextTraps) {
    state.push(interpreter.getFactory().controlTransfer(target, nextTraps));
    return List.of(new DfaInstructionState(interpreter.getInstruction(myJumpOffset.getInstructionOffset()), state));
  }

  @Override
  public int [] getPossibleTargets() {
    return new int[] {myJumpOffset.getInstructionOffset()};
  }

  @Override
  public PsiElement getAnchor() {
    return myAnchor;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " -> " + myJumpOffset;
  }

  public int getJumpOffset() {
    return myJumpOffset.getInstructionOffset();
  }

  public static class TwrFinally extends EnterFinallyTrap {
    public TwrFinally(PsiResourceList resourceList, ControlFlow.ControlFlowOffset offset) {
      super(resourceList, offset);
    }

    @Override
    public List<DfaInstructionState> dispatch(DfaMemoryState state,
                                                                     DataFlowInterpreter interpreter,
                                                                     DfaControlTransferValue.TransferTarget target,
                                                                     FList<DfaControlTransferValue.Trap> nextTraps) {
      if (target instanceof ExceptionTransfer) {
        return DfaControlTransferValue.dispatch(state, interpreter, target, nextTraps);
      }
      return super.dispatch(state, interpreter, target, nextTraps);
    }
  }
}
