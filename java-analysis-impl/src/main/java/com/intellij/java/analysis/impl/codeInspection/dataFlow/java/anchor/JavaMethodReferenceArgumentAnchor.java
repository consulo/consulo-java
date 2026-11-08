// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor;

import com.intellij.java.language.psi.PsiMethodReferenceExpression;

import java.util.Objects;

/**
 * Implicit sole argument of the method reference pushed to the stack.
 * Anchored only in some cases when this method reference is handled by inliner
 */
public class JavaMethodReferenceArgumentAnchor extends JavaDfaAnchor {
  private final PsiMethodReferenceExpression myMethodRef;

  public JavaMethodReferenceArgumentAnchor(PsiMethodReferenceExpression ref) {
    myMethodRef = ref;
  }

  public PsiMethodReferenceExpression getMethodReference() {
    return myMethodRef;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JavaMethodReferenceArgumentAnchor anchor = (JavaMethodReferenceArgumentAnchor)o;
    return Objects.equals(myMethodRef, anchor.myMethodRef);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMethodRef);
  }

  @Override
  public String toString() {
    return "argument of " + myMethodRef.getText();
  }
}
