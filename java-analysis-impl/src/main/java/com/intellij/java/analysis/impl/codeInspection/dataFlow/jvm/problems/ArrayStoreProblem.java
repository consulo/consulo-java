// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems;

import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiType;

public class ArrayStoreProblem extends JvmDfaProblem<PsiAssignmentExpression> {
  private final PsiType myFrom;
  private final PsiType myTo;

  public ArrayStoreProblem(PsiAssignmentExpression anchor, PsiType from, PsiType to) {
    super(anchor);
    myFrom = from;
    myTo = to;
  }

  public PsiType getFromType() {
    return myFrom;
  }

  public PsiType getToType() {
    return myTo;
  }
}
