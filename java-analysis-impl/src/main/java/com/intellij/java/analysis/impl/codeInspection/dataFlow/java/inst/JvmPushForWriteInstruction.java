// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.ir.PushInstruction;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;

/**
 * An instruction that pushes given value to the stack for subsequent write via {@link AssignInstruction}
 * (it additionally processes escaping)
 */
public class JvmPushForWriteInstruction extends PushInstruction {
  public JvmPushForWriteInstruction(DfaValue value) {
    super(value, null);
  }

  @Override
  public Instruction bindToFactory(DfaValueFactory factory) {
    return new JvmPushForWriteInstruction(getValue().bindToFactory(factory));
  }

  @Override
  public String toString() {
    return "PUSH_FOR_WRITE " + getValue();
  }
}
