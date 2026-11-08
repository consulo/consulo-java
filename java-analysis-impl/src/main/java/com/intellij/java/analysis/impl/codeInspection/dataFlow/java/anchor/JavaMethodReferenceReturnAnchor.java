// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor;

import com.intellij.java.language.psi.PsiMethodReferenceExpression;

/**
 * An anchor that points to the Java method reference, whose result is being evaluated
 * by a corresponding instruction. If method reference is wrapped into {@link JavaExpressionAnchor}
 * then the pushed value is method reference itself (functional type). In contrast, if it's wrapped
 * with {@link JavaMethodReferenceReturnAnchor}, the pushed value is result of method reference invocation. 
 */
public class JavaMethodReferenceReturnAnchor extends JavaDfaAnchor {
  private final PsiMethodReferenceExpression myMethodReferenceExpression;

  public JavaMethodReferenceReturnAnchor(PsiMethodReferenceExpression expression) {
    myMethodReferenceExpression = expression;
  }

  public PsiMethodReferenceExpression getMethodReferenceExpression() {
    return myMethodReferenceExpression;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JavaMethodReferenceReturnAnchor anchor = (JavaMethodReferenceReturnAnchor)o;
    return myMethodReferenceExpression.equals(anchor.myMethodReferenceExpression);
  }

  @Override
  public int hashCode() {
    return myMethodReferenceExpression.hashCode();
  }

  @Override
  public String toString() {
    return myMethodReferenceExpression.getText();
  }
}
