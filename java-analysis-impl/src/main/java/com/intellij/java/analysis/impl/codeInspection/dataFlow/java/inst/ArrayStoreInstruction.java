// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems.IndexOutOfBoundsProblem;
import consulo.language.dataFlow.ir.DfaAnchor;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.ExpressionPushingInstruction;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfIntType;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.dataFlow.value.VariableDescriptor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Store array element. Pops (array, index, value) from the stack; pushes stored value
 */
public class ArrayStoreInstruction extends ExpressionPushingInstruction {
  protected final @Nullable DfaControlTransferValue myOutOfBoundsTransfer;
  protected final IndexOutOfBoundsProblem myIndexProblem;
  protected final @Nullable VariableDescriptor myStaticVariable;

  public ArrayStoreInstruction(@Nullable DfaAnchor anchor,
                               IndexOutOfBoundsProblem problem,
                               @Nullable DfaControlTransferValue outOfBoundsTransfer,
                               @Nullable VariableDescriptor variable) {
    super(anchor);
    myIndexProblem = problem;
    myOutOfBoundsTransfer = outOfBoundsTransfer;
    myStaticVariable = variable;
  }

  @Override
  public Instruction bindToFactory(DfaValueFactory factory) {
    DfaControlTransferValue transfer = myOutOfBoundsTransfer == null ? null : myOutOfBoundsTransfer.bindToFactory(factory);
    return new ArrayStoreInstruction(getDfaAnchor(), myIndexProblem, transfer, myStaticVariable);
  }

  @Override
  public DfaInstructionState[] accept(DataFlowInterpreter interpreter, DfaMemoryState stateBefore) {
    DfaValue valueToStore = stateBefore.pop();
    DfaValue index = stateBefore.pop();
    DfaValue array = stateBefore.pop();
    List<DfaInstructionState> finalStates = new ArrayList<>();
    if (myOutOfBoundsTransfer != null) {
      finalStates.addAll(IndexOutOfBoundsProblem.dispatchTransfer(interpreter, stateBefore.createCopy(), myOutOfBoundsTransfer));
    }
    DfaInstructionState[] states =
      myIndexProblem.processOutOfBounds(interpreter, stateBefore, index, array, myOutOfBoundsTransfer);
    if (states != null) return states;

    JavaDfaHelpers.dropLocality(valueToStore, stateBefore);
    checkArrayElementAssignability(interpreter, stateBefore, valueToStore, array);

    LongRangeSet rangeSet = DfIntType.extractRange(stateBefore.getDfType(index));
    DfaValue arrayElementValue = ArrayElementDescriptor.getArrayElementValue(interpreter.getFactory(), array, rangeSet);
    interpreter.getListener().beforeAssignment(valueToStore, arrayElementValue, stateBefore, getDfaAnchor());
    if (arrayElementValue instanceof DfaVariableValue) {
      stateBefore.setVarValue((DfaVariableValue)arrayElementValue, valueToStore);
      interpreter.getListener().afterAssignment(valueToStore, arrayElementValue, stateBefore, getDfaAnchor());
      pushResult(interpreter, stateBefore, arrayElementValue);
    }
    else {
      stateBefore.flushFieldsQualifiedBy(Set.of(array));
      pushResult(interpreter, stateBefore, valueToStore);
    }
    finalStates.add(nextState(interpreter, stateBefore));
    return finalStates.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  protected void checkArrayElementAssignability(DataFlowInterpreter interpreter,
                                                DfaMemoryState memState,
                                                DfaValue dfaSource,
                                                DfaValue qualifier) {
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(DfaValueFactory factory) {
    return List.of(SpecialField.ARRAY_LENGTH);
  }

  @Override
  public String toString() {
    return "ARRAY_STORE " + getDfaAnchor();
  }
}
