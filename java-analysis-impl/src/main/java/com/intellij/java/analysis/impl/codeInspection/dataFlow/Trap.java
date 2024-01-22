/*
 * Copyright 2013-2017 consulo.io
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

package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.language.psi.PsiCatchSection;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiResourceList;
import com.intellij.java.language.psi.PsiTryStatement;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * from kotlin
 */
public abstract class Trap {
  public static class TryCatch extends Trap {
    private final LinkedHashMap<PsiCatchSection, ControlFlow.ControlFlowOffset> clauses;

    public TryCatch(PsiTryStatement tryStatement, LinkedHashMap<PsiCatchSection, ControlFlow.ControlFlowOffset> clauses) {
      super(tryStatement);
      this.clauses = clauses;
    }

    @Override
    List<DfaInstructionState> dispatch(ControlTransferHandler handler) {
      if (handler.getTarget() instanceof ExceptionTransfer) {
        return handler.processCatches(((ExceptionTransfer) handler.getTarget()).getThrowable(), clauses);
      } else {
        return handler.dispatch();
      }
    }

    @Override
    Collection<Integer> getPossibleTargets() {
      return clauses.values().stream().map(ControlFlow.ControlFlowOffset::getInstructionOffset).collect(Collectors.toList());
    }
  }

  public static class TryCatchAll extends Trap {
    private final ControlFlow.ControlFlowOffset target;

    public TryCatchAll(PsiElement anchor, ControlFlow.ControlFlowOffset target) {
      super(anchor);
      this.target = target;
    }

    @Override
    List<DfaInstructionState> dispatch(ControlTransferHandler handler) {
      if (handler.getTarget() instanceof ExceptionTransfer) {
        return Collections.singletonList(new DfaInstructionState(handler.getRunner().getInstruction(target.getInstructionOffset()), handler.getState()));
      } else {
        return handler.dispatch();
      }
    }

    @Override
    Collection<Integer> getPossibleTargets() {
      return Collections.singletonList(target.getInstructionOffset());
    }
  }

  public static abstract class EnterFinally extends Trap {
    private final ControlFlow.ControlFlowOffset jumpOffset;

    protected List<ControlTransferInstruction> backLines = new ArrayList<>();

    public EnterFinally(PsiElement anchor, ControlFlow.ControlFlowOffset jumpOffset) {
      super(anchor);
      this.jumpOffset = jumpOffset;
    }

    public ControlFlow.ControlFlowOffset getJumpOffset() {
      return jumpOffset;
    }

    @Override
    public void link(ControlTransferInstruction instruction) {
      backLines.add(instruction);
    }

    @Override
    List<DfaInstructionState> dispatch(ControlTransferHandler handler) {
      handler.getState().push(handler.getRunner().getFactory().controlTransfer(handler.getTarget(), handler.getTraps()));

      return Collections.singletonList(new DfaInstructionState(handler.getRunner().getInstruction(jumpOffset.getInstructionOffset()), handler.getState()));
    }

    @Override
    Collection<Integer> getPossibleTargets() {
      return Collections.singletonList(jumpOffset.getInstructionOffset());
    }
  }

  public static class TryFinally extends EnterFinally {
    public TryFinally(PsiCodeBlock finallyBlock, ControlFlow.ControlFlowOffset jumpOffset) {
      super(finallyBlock, jumpOffset);
    }
  }

  public static class TwrFinally extends EnterFinally {
    public TwrFinally(PsiResourceList resourceList, ControlFlow.ControlFlowOffset jumpOffset) {
      super(resourceList, jumpOffset);
    }

    @Override
    List<DfaInstructionState> dispatch(ControlTransferHandler handler) {
      if (handler.getTarget() instanceof ExceptionTransfer) {
        return handler.dispatch();
      }
      return super.dispatch(handler);
    }
  }

  public static class InsideFinally extends Trap {
    public InsideFinally(PsiElement finallyBlock) {
      super(finallyBlock);
    }

    @Override
    List<DfaInstructionState> dispatch(ControlTransferHandler handler) {
      @SuppressWarnings("unused")
      DfaControlTransferValue pop = (DfaControlTransferValue) handler.getState().pop();
      return handler.dispatch();
    }
  }

  public static class InsideInlinedBlock extends Trap {
    public InsideInlinedBlock(PsiCodeBlock block) {
      super(block);
    }

    @Override
    List<DfaInstructionState> dispatch(ControlTransferHandler handler) {
      DfaControlTransferValue pop = (DfaControlTransferValue) handler.getState().pop();
      @SuppressWarnings("unused")
      ReturnTransfer r = (ReturnTransfer) pop.getTarget();
      return handler.dispatch();
    }
  }

  private final PsiElement anchor;

  public Trap(@Nonnull PsiElement anchor) {
    this.anchor = anchor;
  }

  @Nonnull
  public PsiElement getAnchor() {
    return anchor;
  }

  public void link(ControlTransferInstruction instruction) {
  }

  abstract List<DfaInstructionState> dispatch(ControlTransferHandler handler);

  Collection<Integer> getPossibleTargets() {
    return Collections.emptyList();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
