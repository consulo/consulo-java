// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems;

import consulo.language.psi.PsiElement;

public class ConsumedStreamProblem extends JvmDfaProblem<PsiElement> {
  public ConsumedStreamProblem(PsiElement anchor) {
    super(anchor);
  }
}
