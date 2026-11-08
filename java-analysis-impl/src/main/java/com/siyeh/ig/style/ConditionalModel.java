// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.java.language.psi.GenericsUtil;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiConditionalExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import org.jspecify.annotations.Nullable;

/**
 * A model which represents a conditional
 */
public class ConditionalModel {
  private final PsiExpression myCondition;
  private final PsiExpression myThenExpression;
  private final PsiExpression myElseExpression;
  private final PsiType myType;

  ConditionalModel(PsiExpression condition,
                          PsiExpression thenExpression,
                          PsiExpression elseExpression,
                          PsiType type) {
    myCondition = condition;
    myThenExpression = thenExpression;
    myElseExpression = elseExpression;
    myType = type;
  }

  /**
   * Condition part of the conditional.
   *
   * @return condition
   */
  public PsiExpression getCondition() {
    return myCondition;
  }

  /**
   * Expression from then branch.
   *
   * @return then expression
   */
  public PsiExpression getThenExpression() {
    return myThenExpression;
  }

  /**
   * Expression from else branch or expression that follows conditional and can be used as else branch.
   *
   * @return else expression
   */
  public PsiExpression getElseExpression() {
    return myElseExpression;
  }

  /**
   * Conditional result type.
   * In case when conditional is an if statement and the type can not be deduced from context the type is determined from types of
   * both branches, essentially interpreting if statement like 'if (cond) then_expr else else_expr' as conditional expression
   * 'cond ? then_expr : else_expr'.
   *
   * @return result type
   * @see ConditionalModel#getType(PsiExpression, PsiExpression, PsiExpression)
   */
  public PsiType getType() {
    return myType;
  }

  /**
   * Converts a conditional expression to a model.
   * Conversion is only possible when the expression is complete and 
   * the branch types are assignable from one to the other or have a common ancestor.
   *
   * @param conditional conditional expression
   * @return null if conditional can't be converted, model otherwise
   */
  public static @Nullable ConditionalModel from(PsiConditionalExpression conditional) {
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(conditional.getCondition());
    if (condition == null) return null;
    PsiExpression thenExpression = conditional.getThenExpression();
    if (thenExpression == null) return null;
    PsiExpression elseExpression = conditional.getElseExpression();
    if (elseExpression == null) return null;
    PsiType type = getType(condition, thenExpression, elseExpression);
    if (type == null) return null;
    return new ConditionalModel(condition, thenExpression, elseExpression, type);
  }

  static @Nullable PsiType getType(PsiExpression condition,
                                   PsiExpression thenExpression,
                                   PsiExpression elseExpression) {
    final PsiType thenType = thenExpression.getType();
    final PsiType elseType = elseExpression.getType();
    if (thenType == null || elseType == null) return null;
    // when one branch is a primitive type and the other the boxed variant of that type the return type is the primitive (jls 15.25)
    if (elseType instanceof PsiPrimitiveType && elseType.equals(PsiPrimitiveType.getUnboxedType(thenType))) return elseType;
    // when the case is reversed this next if already returns the primitive type so no need for a separate check
    if (thenType.isAssignableFrom(elseType)) return thenType;
    if (elseType.isAssignableFrom(thenType)) return elseType;
    if (!(thenType instanceof PsiClassType) || !(elseType instanceof PsiClassType)) return null;
    if (TypeConversionUtil.isPrimitiveWrapper(thenType) || TypeConversionUtil.isPrimitiveWrapper(elseType)) return null;
    return GenericsUtil.getLeastUpperBound(thenType, elseType, condition.getManager());
  }
}
