// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaEndOfInstanceInitializerAnchor;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.dataFlow.value.VariableDescriptor;
import com.intellij.java.language.psi.util.PsiUtil;
import one.util.streamex.StreamEx;

import java.util.List;

public class EndOfInstanceInitializerInstruction extends Instruction {

  @Override
  public DfaInstructionState[] accept(DataFlowInterpreter interpreter,
                                      DfaMemoryState stateBefore) {
    interpreter.getListener().beforePush(DfaValue.EMPTY_ARRAY, interpreter.getFactory().getUnknown(),
                                         new JavaEndOfInstanceInitializerAnchor(), stateBefore);
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(DfaValueFactory factory) {
    return StreamEx.of(factory.getValues()).select(DfaVariableValue.class)
      .map(DfaVariableValue::getDescriptor)
      .filter(var -> !PsiUtil.isJvmLocalVariable(var.getPsiElement())).toList();
  }

  @Override
  public String toString() {
    return "END_OF_INSTANCE_INITIALIZER";
  }
}
