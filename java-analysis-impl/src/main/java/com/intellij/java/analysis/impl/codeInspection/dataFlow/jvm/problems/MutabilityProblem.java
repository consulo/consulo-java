// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems;

import consulo.language.psi.PsiElement;

public class MutabilityProblem extends JvmDfaProblem<PsiElement> {
  private final boolean myReceiver;

  public MutabilityProblem(PsiElement anchor, boolean receiver) {
    super(anchor);
    myReceiver = receiver;
  }

  public boolean isReceiver() {
    return myReceiver;
  }
}
