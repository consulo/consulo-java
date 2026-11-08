// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.JavaDfaHelpers;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.dataFlow.value.VariableDescriptor;
import one.util.streamex.StreamEx;

import java.util.List;

/**
 * Marks given variables as escaped (usually necessary for captured variables in lambdas/local classes)
 */
public class EscapeInstruction extends Instruction {
  private final List<VariableDescriptor> myEscapedDescriptors;

  public EscapeInstruction(List<VariableDescriptor> descriptors) {
    myEscapedDescriptors = descriptors;
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(DfaValueFactory factory) {
    return myEscapedDescriptors;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowInterpreter interpreter, DfaMemoryState stateBefore) {
    List<DfaVariableValue> escapedVars = StreamEx.of(interpreter.getFactory().getValues())
      .select(DfaVariableValue.class)
      .filter(var -> myEscapedDescriptors.contains(var.getDescriptor()))
      .toList();
    for (DfaVariableValue var : escapedVars) {
      JavaDfaHelpers.dropLocality(var, stateBefore);
    }
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public String toString() {
    return "ESCAPE " + myEscapedDescriptors;
  }
}
