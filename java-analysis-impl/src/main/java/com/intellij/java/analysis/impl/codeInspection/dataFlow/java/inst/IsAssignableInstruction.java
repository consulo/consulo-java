// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import consulo.language.dataFlow.ir.EvalInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiType;

/**
 * A binary operation that takes two types from the stack and returns
 * whether one is assignable from another
 */
public class IsAssignableInstruction extends EvalInstruction {
  public IsAssignableInstruction(PsiMethodCallExpression expression) {
    super(new JavaExpressionAnchor(expression), 2);
  }

  @Override
  public DfaValue eval(DfaValueFactory factory, DfaMemoryState state, DfaValue ... arguments) {
    PsiType superClass = state.getDfType(arguments[1]).getConstantOfType(PsiType.class);
    PsiType subClass = state.getDfType(arguments[0]).getConstantOfType(PsiType.class);
    if (superClass != null && subClass != null) {
      TypeConstraint superType = TypeConstraints.instanceOf(superClass);
      TypeConstraint subType = TypeConstraints.instanceOf(subClass);
      if (subType.meet(superType) == TypeConstraints.BOTTOM) {
        return factory.fromDfType(DfTypes.FALSE);
      } else {
        TypeConstraint negated = subType.tryNegate();
        if (negated != null && negated.meet(superType) == TypeConstraints.BOTTOM) {
          return factory.fromDfType(DfTypes.TRUE);
        }
      }
    }
    return factory.getUnknown();
  }

  @Override
  public String toString() {
    return "IS_ASSIGNABLE_FROM";
  }
}
