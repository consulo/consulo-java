// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import consulo.language.dataFlow.ir.DfaAnchor;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.ExpressionPushingInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaValue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.FALSE;
import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.TRUE;

public class NotInstruction extends ExpressionPushingInstruction {
  public NotInstruction(@Nullable DfaAnchor anchor) {
    super(anchor);
  }

  @Override
  public DfaInstructionState[] accept(DataFlowInterpreter interpreter, DfaMemoryState stateBefore) {
    DfaValue dfaValue = stateBefore.pop();

    DfaMemoryState falseState = stateBefore.createCopy();
    List<DfaInstructionState> result = new ArrayList<>(2);
    if (stateBefore.applyCondition(dfaValue.eq(FALSE))) {
      pushResult(interpreter, stateBefore, TRUE);
      result.add(nextState(interpreter, stateBefore));
    }
    if (falseState.applyCondition(dfaValue.eq(TRUE))) {
      pushResult(interpreter, falseState, FALSE);
      result.add(nextState(interpreter, falseState));
    }

    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  @Override
  public String toString() {
    return "NOT";
  }
}
