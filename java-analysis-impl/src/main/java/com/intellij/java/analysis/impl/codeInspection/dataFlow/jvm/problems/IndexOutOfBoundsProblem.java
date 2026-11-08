// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import consulo.language.dataFlow.interpreter.UnsatisfiedConditionProblem;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DerivedVariableDescriptor;
import consulo.language.dataFlow.value.DfaCondition;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.RelationType;
import consulo.util.lang.ThreeState;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.intValue;

public interface IndexOutOfBoundsProblem extends UnsatisfiedConditionProblem {
  /**
   * @return a descriptor that describes the length that the index should not exceed.
   * An index must be not less than zero and less than the length.
   */
  DerivedVariableDescriptor getLengthDescriptor();

  private boolean applyBoundsCheck(DfaMemoryState memState,
                                  DfaValue array,
                                  DfaValue index) {
    DfaValueFactory factory = index.getFactory();
    DfaValue length = getLengthDescriptor().createValue(factory, array);
    DfaCondition lengthMoreThanZero = length.cond(RelationType.GT, intValue(0));
    if (!memState.applyCondition(lengthMoreThanZero)) return false;
    DfaCondition indexNonNegative = index.cond(RelationType.GE, intValue(0));
    if (!memState.applyCondition(indexNonNegative)) return false;
    DfaCondition indexLessThanLength = index.cond(RelationType.LT, length);
    if (!memState.applyCondition(indexLessThanLength)) return false;
    return true;
  }

  default DfaInstructionState @Nullable [] processOutOfBounds(DataFlowInterpreter interpreter,
                                                              DfaMemoryState stateBefore,
                                                              DfaValue index,
                                                              DfaValue array,
                                                              @Nullable DfaControlTransferValue outOfBoundsTransfer) {
    boolean alwaysOutOfBounds = !applyBoundsCheck(stateBefore, array, index);
    ThreeState failed = alwaysOutOfBounds ? ThreeState.YES : ThreeState.UNSURE;
    interpreter.getListener().onCondition(this, index, failed, stateBefore);
    if (alwaysOutOfBounds) {
      if (outOfBoundsTransfer != null) {
        List<DfaInstructionState> states = dispatchTransfer(interpreter, stateBefore, outOfBoundsTransfer);
        return states.toArray(DfaInstructionState.EMPTY_ARRAY);
      }
      return DfaInstructionState.EMPTY_ARRAY;
    }
    return null;
  }

  static List<DfaInstructionState> dispatchTransfer(DataFlowInterpreter interpreter,
                                                             DfaMemoryState stateBefore,
                                                             DfaControlTransferValue outOfBoundsTransfer) {
    List<DfaInstructionState> states = outOfBoundsTransfer.dispatch(stateBefore, interpreter);
    for (DfaInstructionState state : states) {
      state.getMemoryState().markEphemeral();
    }
    return states;
  }
}
