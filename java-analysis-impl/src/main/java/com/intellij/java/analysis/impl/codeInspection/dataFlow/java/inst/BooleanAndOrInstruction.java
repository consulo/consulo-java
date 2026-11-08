// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import consulo.language.dataFlow.ir.DfaAnchor;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.ExpressionPushingInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaCondition;
import consulo.language.dataFlow.value.DfaValue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.booleanValue;

/**
 * Non-short-circuit and/or instruction
 */
public class BooleanAndOrInstruction extends ExpressionPushingInstruction {
  private final boolean myOr;

  /**
   * @param or whether it's 'or' (otherwise, it's 'and')
   * @param anchor to bind instruction to
   */
  public BooleanAndOrInstruction(boolean or, @Nullable DfaAnchor anchor) {
    super(anchor);
    myOr = or;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowInterpreter interpreter, DfaMemoryState stateBefore) {
    DfaValue dfaRight = stateBefore.pop();
    DfaValue dfaLeft = stateBefore.pop();

    List<DfaInstructionState> result = new ArrayList<>(2);
    DfaMemoryState copy = stateBefore.createCopy();
    DfaCondition cond = dfaRight.eq(booleanValue(myOr));
    if (copy.applyCondition(cond)) {
      pushResult(interpreter, copy, booleanValue(myOr));
      result.add(nextState(interpreter, copy));
    }
    if (stateBefore.applyCondition(cond.negate())) {
      pushResult(interpreter, stateBefore, dfaLeft);
      result.add(nextState(interpreter, stateBefore));
    }
    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  @Override
  public String toString() {
    return myOr ? "OR" : "AND";
  }
}
