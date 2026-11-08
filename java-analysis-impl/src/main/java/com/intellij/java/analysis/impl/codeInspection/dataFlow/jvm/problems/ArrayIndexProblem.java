// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.SpecialField;
import consulo.language.dataFlow.value.DerivedVariableDescriptor;
import com.intellij.java.language.psi.PsiArrayAccessExpression;

public class ArrayIndexProblem extends JvmDfaProblem<PsiArrayAccessExpression> implements IndexOutOfBoundsProblem {
  public ArrayIndexProblem(PsiArrayAccessExpression anchor) {
    super(anchor);
  }

  @Override
  public DerivedVariableDescriptor getLengthDescriptor() {
    return SpecialField.ARRAY_LENGTH;
  }
}
