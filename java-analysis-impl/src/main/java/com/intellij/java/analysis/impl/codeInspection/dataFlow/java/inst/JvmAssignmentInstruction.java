// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.JavaDfaHelpers;
import consulo.language.dataFlow.ir.DfaAnchor;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.ir.SimpleAssignmentInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import org.jspecify.annotations.Nullable;

/**
 * Assign the value from the stack to a specified static destination
 */
public class JvmAssignmentInstruction extends SimpleAssignmentInstruction {
  public JvmAssignmentInstruction(@Nullable DfaAnchor anchor, DfaVariableValue destination) {
    super(anchor, destination);
  }
  
  @Override
  public Instruction bindToFactory(DfaValueFactory factory) {
    return new JvmAssignmentInstruction(getDfaAnchor(), getDestination().bindToFactory(factory));
  }

  @Override
  public DfaInstructionState[] accept(DataFlowInterpreter interpreter,
                                      DfaMemoryState stateBefore) {
    DfaValue value = stateBefore.peek();
    if (value instanceof DfaVariableValue) {
      if (!DfaValueFactory.isTempVariable((DfaVariableValue)value)) {
        JavaDfaHelpers.dropLocality(value, stateBefore);
      }
    }
    return super.accept(interpreter, stateBefore);
  }
}
