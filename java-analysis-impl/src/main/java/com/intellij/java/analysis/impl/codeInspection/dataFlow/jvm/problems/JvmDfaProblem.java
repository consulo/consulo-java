// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems;

import consulo.language.dataFlow.interpreter.UnsatisfiedConditionProblem;
import consulo.language.psi.PsiElement;

public abstract class JvmDfaProblem<T extends PsiElement> implements UnsatisfiedConditionProblem {
  private final T myAnchor;

  protected JvmDfaProblem(T anchor) { 
    myAnchor = anchor; 
  }

  public T getAnchor() {
    return myAnchor;
  }
}
