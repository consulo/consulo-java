// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems.IndexOutOfBoundsProblem;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import org.jspecify.annotations.Nullable;

/**
 * Pops container and its index from the stack and ensures that the index (int value) is within the container length
 * Currently, used in Kotlin only
 */
public class EnsureIndexInBoundsInstruction extends Instruction {
  private final IndexOutOfBoundsProblem myProblem;
  private final @Nullable DfaControlTransferValue myOutOfBoundsTransfer;

  public EnsureIndexInBoundsInstruction(IndexOutOfBoundsProblem problem,
                                        @Nullable DfaControlTransferValue transfer) {
    myProblem = problem;
    myOutOfBoundsTransfer = transfer;
  }

  @Override
  public Instruction bindToFactory(DfaValueFactory factory) {
    DfaControlTransferValue newTransfer = myOutOfBoundsTransfer == null ? null : myOutOfBoundsTransfer.bindToFactory(factory);
    return new EnsureIndexInBoundsInstruction(myProblem, newTransfer);
  }

  @Override
  public DfaInstructionState[] accept(DataFlowInterpreter interpreter,
                                      DfaMemoryState stateBefore) {
    DfaValue index = stateBefore.pop();
    DfaValue container = stateBefore.pop();
    DfaInstructionState[] states = myProblem.processOutOfBounds(interpreter, stateBefore, index, container, myOutOfBoundsTransfer);
    if (states != null) return states;
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public String toString() {
    return "ENSURE_INDEX " + myProblem.getLengthDescriptor();
  }
}
