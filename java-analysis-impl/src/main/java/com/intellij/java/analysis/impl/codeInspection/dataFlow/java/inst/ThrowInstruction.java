// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer.ExceptionTransfer;
import consulo.language.dataFlow.ir.ControlTransferInstruction;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Throw Java exception
 */
public class ThrowInstruction extends ControlTransferInstruction {
  private final @Nullable PsiElement myAnchor;

  public ThrowInstruction(DfaControlTransferValue transfer, @Nullable PsiElement anchor) {
    this(transfer, anchor, true);
  }

  private ThrowInstruction(DfaControlTransferValue transfer, @Nullable PsiElement anchor, boolean linkTraps) {
    super(transfer, linkTraps);
    assert transfer.getTarget() instanceof ExceptionTransfer;
    myAnchor = anchor;
  }

  @Override
  public Instruction bindToFactory(DfaValueFactory factory) {
    return new ThrowInstruction(getTransfer().bindToFactory(factory), myAnchor, false);
  }

  public @Nullable PsiElement getAnchor() {
    return myAnchor;
  }

  @Override
  public String toString() {
    int[] indexes = getSuccessorIndexes();
    return "THROW " +
           ((ExceptionTransfer)getTransfer().getTarget()).getThrowable() + 
           (indexes.length == 0 ? "" : " [targets: " + Arrays.toString(indexes) + "]");
  }
}
