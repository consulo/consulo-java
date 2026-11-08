// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems;

import com.intellij.java.language.psi.PsiExpression;

public class NegativeArraySizeProblem extends JvmDfaProblem<PsiExpression> {
  public NegativeArraySizeProblem(PsiExpression anchor) {
    super(anchor);
  }
}
