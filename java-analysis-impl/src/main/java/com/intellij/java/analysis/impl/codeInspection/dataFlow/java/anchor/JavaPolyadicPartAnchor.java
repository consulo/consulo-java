// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor;

import consulo.document.util.TextRange;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPolyadicExpression;

import java.util.Objects;

/**
 * An anchor that points to the prefix part of the polyadic expression
 * whose result is being evaluated.
 */
public class JavaPolyadicPartAnchor extends JavaDfaAnchor {
  private final PsiPolyadicExpression myExpression;
  private final int myLastOperand;

  public JavaPolyadicPartAnchor(PsiPolyadicExpression expression, int operand) {
    if (operand < 0) throw new IllegalArgumentException();
    myExpression = expression;
    myLastOperand = operand;
  }

  /**
   * @return an expression, whose part is being evaluated
   */
  public PsiPolyadicExpression getExpression() {
    return myExpression;
  }

  /**
   * @return a text range inside the {@linkplain #getExpression() expression} that
   * designates the evaluated part
   */
  public TextRange getTextRange() {
    PsiExpression[] operands = myExpression.getOperands();
    if (operands.length > myLastOperand + 1) {
      return new TextRange(0, operands[myLastOperand].getStartOffsetInParent() + operands[myLastOperand].getTextLength());
    }
    throw new IllegalStateException("Not enough operands");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JavaPolyadicPartAnchor anchor = (JavaPolyadicPartAnchor)o;
    return myLastOperand == anchor.myLastOperand && myExpression.equals(anchor.myExpression);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myExpression, myLastOperand);
  }

  @Override
  public String toString() {
    return getTextRange().substring(myExpression.getText());
  }
}
