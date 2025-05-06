// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import consulo.java.language.module.util.JavaClassNames;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

import jakarta.annotation.Nonnull;

/**
 * Represents a model of PsiExpression which checks whether two expressions are equal (via Object.equals directly or indirectly).
 */
public class EqualityCheck {
  private static final CallMatcher OBJECT_EQUALS = CallMatcher.anyOf(
      CallMatcher.staticCall(JavaClassNames.JAVA_UTIL_OBJECTS, "equals").parameterCount(2),
      CallMatcher.staticCall("com.google.common.base.Objects", "equal").parameterCount(2));
  private final
  @Nonnull
  PsiExpression myLeft;
  private final
  @Nonnull
  PsiExpression myRight;
  private final boolean myLeftDereferenced;

  private EqualityCheck(@Nonnull PsiExpression left, @Nonnull PsiExpression right, boolean leftDereferenced) {
    myLeft = left;
    myRight = right;
    myLeftDereferenced = leftDereferenced;
  }

  /**
   * @param expression to create an {@link EqualityCheck} from
   * @return an {@link EqualityCheck} which represents an equality check performed on given expression; null if equality check
   * was not found in given expression.
   */
  @Nullable
  @Contract("null -> null")
  public static EqualityCheck from(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression) expression;
      if (MethodCallUtils.isEqualsCall(call)) {
        PsiExpression left = call.getMethodExpression().getQualifierExpression();
        PsiExpression right = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
        if (left == null || right == null)
          return null;
        return new EqualityCheck(left, right, true);
      }
      if (OBJECT_EQUALS.test(call)) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        return new EqualityCheck(args[0], args[1], false);
      }
    } else if (expression instanceof PsiConditionalExpression) {
      PsiConditionalExpression ternary = (PsiConditionalExpression) expression;
      EqualityCheck nestedCheck = from(ternary.getThenExpression());
      PsiExpression other = ternary.getElseExpression();
      boolean equalsToNull = false;
      if (nestedCheck == null) {
        nestedCheck = from(ternary.getElseExpression());
        other = ternary.getThenExpression();
        equalsToNull = true;
      }
      if (nestedCheck != null && nestedCheck.isLeftDereferenced() && other != null) {
        PsiReferenceExpression leftRef = ExpressionUtils.getReferenceExpressionFromNullComparison(ternary.getCondition(), equalsToNull);
        EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
        if (equivalence.expressionsAreEquivalent(leftRef, nestedCheck.getLeft())) {
          PsiReferenceExpression rightRef = ExpressionUtils.getReferenceExpressionFromNullComparison(other, true);
          if (equivalence.expressionsAreEquivalent(rightRef, nestedCheck.getRight())) {
            return new EqualityCheck(nestedCheck.getLeft(), nestedCheck.getRight(), false);
          }
        }
      }
    }
    return null;
  }

  @Nonnull
  public PsiExpression getLeft() {
    return myLeft;
  }

  @Nonnull
  public PsiExpression getRight() {
    return myRight;
  }

  public boolean isLeftDereferenced() {
    return myLeftDereferenced;
  }
}
