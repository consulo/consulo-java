// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import consulo.language.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems.ClassCastProblem;
import consulo.language.dataFlow.ir.DfaAnchor;
import consulo.language.dataFlow.interpreter.UnsatisfiedConditionProblem;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.ExpressionPushingInstruction;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.value.DfaCondition;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.dataFlow.value.RelationType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeCastExpression;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.util.lang.ThreeState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.NULL;
import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.typedObject;

public class TypeCastInstruction extends ExpressionPushingInstruction {
  private final PsiExpression myCasted;
  private final PsiType myCastTo;
  private final @Nullable DfaControlTransferValue myTransferValue;

  public TypeCastInstruction(PsiTypeCastExpression castExpression,
                             PsiExpression casted,
                             PsiType castTo,
                             @Nullable DfaControlTransferValue value) {
    this(new JavaExpressionAnchor(castExpression), casted, castTo, value);
  }
  
  private TypeCastInstruction(DfaAnchor castExpression,
                             PsiExpression casted,
                             PsiType castTo,
                             @Nullable DfaControlTransferValue value) {
    super(castExpression);
    assert !(castTo instanceof PsiPrimitiveType);
    myCasted = casted;
    myCastTo = TypeConversionUtil.erasure(castTo);
    myTransferValue = value;
  }

  @Override
  public Instruction bindToFactory(DfaValueFactory factory) {
    if (myTransferValue == null) return this;
    return new TypeCastInstruction(getDfaAnchor(), myCasted, myCastTo, myTransferValue.bindToFactory(factory));
  }

  public PsiExpression getCasted() {
    return myCasted;
  }

  public @Nullable UnsatisfiedConditionProblem getConditionProblem() {
    JavaExpressionAnchor anchor = (JavaExpressionAnchor)getDfaAnchor();
    return anchor == null ? null : new ClassCastProblem(((PsiTypeCastExpression)anchor.getExpression()));
  }

  @Override
  public DfaInstructionState[] accept(DataFlowInterpreter interpreter, DfaMemoryState stateBefore) {
    final DfaValueFactory factory = interpreter.getFactory();
    PsiType fromType = getCasted().getType();
    TypeConstraint constraint = TypeConstraints.instanceOf(myCastTo);
    boolean castPossible = true;
    List<DfaInstructionState> result = new ArrayList<>();
    if (myTransferValue != null) {
      DfaMemoryState castFail = stateBefore.createCopy();
      if (fromType != null && (!constraint.isResolved() || myCastTo.isConvertibleFrom(fromType))) {
        if (!castTopOfStack(factory, stateBefore, constraint)) {
          castPossible = false;
        } else {
          result.add(nextState(interpreter, stateBefore));
          pushResult(interpreter, stateBefore, stateBefore.pop());
        }
      }
      DfaValue value = castFail.peek();
      DfaCondition notNullCondition = value.cond(RelationType.NE, NULL);
      DfaCondition notTypeCondition = value.cond(RelationType.IS_NOT, typedObject(myCastTo, Nullability.NOT_NULL));
      if (castFail.applyCondition(notNullCondition) && castFail.applyCondition(notTypeCondition)) {
        List<DfaInstructionState> states = myTransferValue.dispatch(castFail, interpreter);
        for (DfaInstructionState cceState : states) {
          cceState.getMemoryState().markEphemeral();
        }
        result.addAll(states);
      }
    } else {
      if (fromType != null && myCastTo.isConvertibleFrom(fromType)) {
        if (!castTopOfStack(factory, stateBefore, constraint)) {
          castPossible = false;
        }
      }

      if (castPossible || interpreter instanceof StandardDataFlowInterpreter standard && !standard.stopOnCast()) {
        result.add(nextState(interpreter, stateBefore));
      }
      pushResult(interpreter, stateBefore, stateBefore.pop());
    }
    UnsatisfiedConditionProblem problem = getConditionProblem();
    if (problem != null) {
      interpreter.getListener().onCondition(problem, stateBefore.peek(), castPossible ? ThreeState.UNSURE : ThreeState.YES, stateBefore);
    }
    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  private static boolean castTopOfStack(DfaValueFactory factory,
                                        DfaMemoryState state,
                                        TypeConstraint type) {
    DfaValue value = state.peek();
    DfType dfType = state.getDfType(value);
    DfType result = dfType.meet(type.asDfType());
    if (!result.equals(dfType)) {
      if (result == NULL || !state.meetDfType(value, result)) return false;
      if (!(value instanceof DfaVariableValue)) {
        state.pop();
        state.push(factory.fromDfType(result));
      }
    }
    return true;
  }

  @Override
  public String toString() {
    return "CAST_TO "+myCastTo.getCanonicalText();
  }
}
