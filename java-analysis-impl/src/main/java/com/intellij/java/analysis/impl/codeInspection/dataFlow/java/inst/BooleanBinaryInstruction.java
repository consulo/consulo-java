// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import consulo.language.dataFlow.ir.DfaAnchor;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.ExpressionPushingInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.types.DfConstantType;
import consulo.language.dataFlow.value.DfaCondition;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaWrappedValue;
import consulo.language.dataFlow.value.RelationType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.BOOLEAN;
import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.FALSE;
import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.booleanValue;

/**
 * Evaluate comparison like a < b
 */
public class BooleanBinaryInstruction extends ExpressionPushingInstruction {
  private final RelationType myRelation;
  private final boolean myForceEqualityByContent;

  /**
   * @param relation               operation relation
   * @param forceEqualityByContent if true, equality by content will be used instead of reference equality where supported
   *                               (e.g., for strings)
   * @param anchor                 to bind instruction to
   */
  public BooleanBinaryInstruction(RelationType relation, boolean forceEqualityByContent, @Nullable DfaAnchor anchor) {
    super(anchor);
    myRelation = relation;
    myForceEqualityByContent = forceEqualityByContent;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowInterpreter interpreter, DfaMemoryState stateBefore) {
    DfaValue dfaRight = stateBefore.pop();
    DfaValue dfaLeft = stateBefore.pop();

    if ((myRelation == RelationType.EQ || myRelation == RelationType.NE) &&
        !myForceEqualityByContent && shouldCompareByEquals(stateBefore, dfaLeft, dfaRight)) {
      ArrayList<DfaInstructionState> states = new ArrayList<>(2);
      DfaMemoryState equality = stateBefore.createCopy();
      DfaCondition condition = dfaLeft.eq(dfaRight);
      if (equality.applyCondition(condition)) {
        pushResult(interpreter, equality, BOOLEAN);
        states.add(nextState(interpreter, equality));
      }
      if (stateBefore.applyCondition(condition.negate())) {
        pushResult(interpreter, stateBefore, booleanValue(myRelation == RelationType.NE));
        states.add(nextState(interpreter, stateBefore));
      }
      return states.toArray(DfaInstructionState.EMPTY_ARRAY);
    }
    RelationType[] relations = splitRelation(myRelation);

    ArrayList<DfaInstructionState> states = new ArrayList<>(relations.length);

    for (int i = 0; i < relations.length; i++) {
      RelationType relation = relations[i];
      boolean result = myRelation.isSubRelation(relation);
      DfaCondition condition = dfaLeft.cond(relation, dfaRight).correctForRelationResult(result);
      if (condition == DfaCondition.getFalse()) continue;
      if (condition == DfaCondition.getTrue()) {
        pushResult(interpreter, stateBefore, booleanValue(result));
        return nextStates(interpreter, stateBefore);
      }
      final DfaMemoryState copy = i == relations.length - 1 && !states.isEmpty() ? stateBefore : stateBefore.createCopy();
      if (copy.applyCondition(condition) &&
          copy.meetDfType(dfaLeft, copy.getDfType(dfaLeft).correctForRelationResult(myRelation, result)) &&
          copy.meetDfType(dfaRight, copy.getDfType(dfaRight).correctForRelationResult(myRelation, result))) {
        pushResult(interpreter, copy, booleanValue(result));
        states.add(nextState(interpreter, copy));
      }
    }
    if (states.isEmpty()) {
      // Neither of relations could be applied: likely comparison with NaN; do not split the state in this case, just push false
      pushResult(interpreter, stateBefore, FALSE);
      return nextStates(interpreter, stateBefore);
    }

    return states.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  private static RelationType [] splitRelation(RelationType relationType) {
    return switch (relationType) {
      case LT, LE, GT, GE -> new RelationType[]{RelationType.LT, RelationType.GT, RelationType.EQ};
      default -> new RelationType[]{relationType, relationType.getNegated()};
    };
  }

  /**
   * Returns true if two given values should be compared by content, rather than by reference.
   * @param memState memory state
   * @param dfaLeft left value
   * @param dfaRight right value
   * @return true if two given values should be compared by content, rather than by reference.
   */
  private static boolean shouldCompareByEquals(DfaMemoryState memState, DfaValue dfaLeft, DfaValue dfaRight) {
    if (dfaLeft == dfaRight && !(dfaLeft instanceof DfaWrappedValue) && !(dfaLeft.getDfType() instanceof DfConstantType)) {
      return false;
    }
    return TypeConstraint.fromDfType(memState.getDfType(dfaLeft)).isComparedByEquals() &&
           TypeConstraint.fromDfType(memState.getDfType(dfaRight)).isComparedByEquals();

  }

  @Override
  public String toString() {
    return "BOOLEAN_OP " + myRelation;
  }
}
