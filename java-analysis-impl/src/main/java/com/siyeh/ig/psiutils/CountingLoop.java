// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaPsiUtil;
import consulo.language.dataFlow.value.RelationType;
import com.intellij.java.language.psi.PsiBinaryExpression;
import com.intellij.java.language.psi.PsiDeclarationStatement;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiForStatement;
import com.intellij.java.language.psi.PsiLocalVariable;
import com.intellij.java.language.psi.PsiLoopStatement;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypes;
import consulo.language.ast.IElementType;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import org.jspecify.annotations.Nullable;

import static consulo.util.lang.ObjectUtil.tryCast;

/**
 * Represents a loop of form {@code for(int/long counter = initializer; counter </<= bound; counter++/--)}
 */
public final class CountingLoop {
  final PsiLocalVariable myCounter;
  final PsiLoopStatement myLoop;
  final PsiExpression myInitializer;
  final PsiExpression myBound;
  final boolean myIncluding;
  final LoopDirection myDirection;
  final boolean myMayOverflow;

  private CountingLoop(PsiLoopStatement loop,
                       PsiLocalVariable counter,
                       PsiExpression initializer,
                       PsiExpression bound,
                       boolean including,
                       LoopDirection direction,
                       boolean mayOverflow) {
    myInitializer = initializer;
    myCounter = counter;
    myLoop = loop;
    myBound = bound;
    myIncluding = including;
    myDirection = direction;
    myMayOverflow = mayOverflow;
  }

  /**
   * @return loop counter variable
   */
  public PsiLocalVariable getCounter() {
    return myCounter;
  }

  /**
   * @return loop statement
   */
  public PsiLoopStatement getLoop() {
    return myLoop;
  }

  /**
   * @return counter variable initial value
   */
  public PsiExpression getInitializer() {
    return myInitializer;
  }

  /**
   * @return loop bound
   */
  public PsiExpression getBound() {
    return myBound;
  }

  /**
   * @return true if bound is including
   */
  public boolean isIncluding() {
    return myIncluding;
  }

  /**
   * @return true if the loop is descending
   */
  public boolean isDescending() {
    return myDirection == LoopDirection.DESCENDING;
  }

  /**
   * @return true if the loop variable may experience integer overflow before reaching the bound,
   * like for(int i = 10; i != -10; i++) will go through MAX_VALUE and MIN_VALUE.
   */
  public boolean mayOverflow() {
    return myMayOverflow;
  }

  public static @Nullable CountingLoop from(PsiForStatement forStatement) {
    // check that initialization is for(int/long i = <initial_value>;...;...)
    PsiDeclarationStatement initialization = tryCast(forStatement.getInitialization(), PsiDeclarationStatement.class);
    if (initialization == null) return null;
    PsiElement[] declaredElements = initialization.getDeclaredElements();
    if (declaredElements.length != 1) return null;
    PsiLocalVariable counter = tryCast(declaredElements[0], PsiLocalVariable.class);
    if(counter == null) return null;
    PsiType counterType = counter.getType();
    if(!counterType.equals(PsiTypes.intType()) && !counterType.equals(PsiTypes.longType())) return null;

    PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(counter.getInitializer());
    if(initializer == null) return null;

    // check that increment is like for(...;...;i++)
    LoopDirection loopDirection = LoopDirection.evaluateLoopDirection(counter, forStatement.getUpdate());
    if (loopDirection == null) return null;

    // check that condition is like for(...;i<bound;...) or for(...;i<=bound;...)
    PsiBinaryExpression condition = tryCast(PsiUtil.skipParenthesizedExprDown(forStatement.getCondition()), PsiBinaryExpression.class);
    if(condition == null) return null;
    IElementType type = condition.getOperationTokenType();
    boolean closed = false;
    RelationType relationType = DfaPsiUtil.getRelationByToken(type);
    if (relationType == null || !relationType.isInequality()) return null;
    if (relationType.isSubRelation(RelationType.EQ)) {
      closed = true;
    }
    if (loopDirection == LoopDirection.DESCENDING) {
      relationType = relationType.getFlipped();
      assert relationType != null;
    }
    PsiExpression bound = ExpressionUtils.getOtherOperand(condition, counter);
    if (bound == null) return null;
    if (bound == condition.getLOperand()) {
      relationType = relationType.getFlipped();
      assert relationType != null;
    }
    if (!relationType.isSubRelation(RelationType.LT)) return null;
    if(!TypeConversionUtil.areTypesAssignmentCompatible(counterType, bound)) return null;
    if(VariableAccessUtils.variableIsAssigned(counter, forStatement.getBody())) return null;
    return new CountingLoop(forStatement, counter, initializer, bound, closed, loopDirection, relationType == RelationType.NE);
  }
}
