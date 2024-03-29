// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.language.impl.psi.controlFlow;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;

public final class LocalsOrMyInstanceFieldsControlFlowPolicy implements ControlFlowPolicy {
  private static final LocalsOrMyInstanceFieldsControlFlowPolicy INSTANCE = new LocalsOrMyInstanceFieldsControlFlowPolicy();

  private LocalsOrMyInstanceFieldsControlFlowPolicy() {
  }

  @Override
  public PsiVariable getUsedVariable(@Nonnull PsiReferenceExpression refExpr) {
    if (isLocalOrMyInstanceReference(refExpr)) {
      return ObjectUtil.tryCast(refExpr.resolve(), PsiVariable.class);
    }
    return null;
  }

  @Override
  public boolean isParameterAccepted(@Nonnull PsiParameter psiParameter) {
    return true;
  }

  @Override
  public boolean isLocalVariableAccepted(@Nonnull PsiLocalVariable psiVariable) {
    return true;
  }

  public static LocalsOrMyInstanceFieldsControlFlowPolicy getInstance() {
    return INSTANCE;
  }

  /**
   * @param variableReference variable reference to check
   * @return true if given variable reference refers to local variable or the field which participates in
   * definitive assignment analysis, as specified in JLS, chapter 16. The method does not check whether
   * the reference actually resolves to variable.
   */
  public static boolean isLocalOrMyInstanceReference(PsiReferenceExpression variableReference) {
    PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(variableReference.getQualifierExpression());
    // JLS 16: "Such an assignment is defined to occur if and only if either the simple name of the variable
    // (or, for a field, its simple name qualified by this) occurs on the left hand side of an assignment operator"
    // Qualified this is not allowed by spec
    return qualifierExpression == null || (qualifierExpression instanceof PsiThisExpression &&
        ((PsiThisExpression) qualifierExpression).getQualifier() == null);
  }
}
