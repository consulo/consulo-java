// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.transfer;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaDfaNullability;
import consulo.language.dataFlow.types.DfaNullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.dataFlow.value.DfaControlTransferValue.Trap;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.dataFlow.value.VariableDescriptor;
import com.intellij.java.language.psi.PsiCatchSection;
import com.intellij.java.language.psi.PsiDisjunctionType;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.FList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public class TryCatchTrap implements Trap {
  private final PsiElement myStatement;
  private final LinkedHashMap<CatchClauseDescriptor, ? extends ControlFlow.ControlFlowOffset> myClauses;

  public TryCatchTrap(PsiElement tryStatement,
                      LinkedHashMap<CatchClauseDescriptor, ? extends ControlFlow.ControlFlowOffset> clauses) {
    myStatement = tryStatement;
    myClauses = clauses;
  }

  public interface CatchClauseDescriptor {
    List<TypeConstraint> constraints();
    @Nullable VariableDescriptor parameter();
  }

  public static class JavaCatchClauseDescriptor implements CatchClauseDescriptor {
    private final PsiCatchSection mySection;

    public JavaCatchClauseDescriptor(PsiCatchSection section) {
      mySection = section;
    }

    @Override
    public List<TypeConstraint> constraints() {
      PsiParameter parameter = mySection.getParameter();
      if (parameter == null) return Collections.emptyList();
      PsiType type = parameter.getType();
      List<PsiType> types = type instanceof PsiDisjunctionType ? ((PsiDisjunctionType)type).getDisjunctions() : List.of(type);
      return ContainerUtil.map(types, TypeConstraints::instanceOf);
    }

    @Override
    public @Nullable VariableDescriptor parameter() {
      PsiParameter parameter = mySection.getParameter();
      return parameter == null ? null : new PlainDescriptor(parameter);
    }
  }

  @Override
  public List<DfaInstructionState> dispatch(DfaMemoryState state,
                                                                   DataFlowInterpreter interpreter,
                                                                   DfaControlTransferValue.TransferTarget target,
                                                                   FList<Trap> nextTraps) {
    if (!(target instanceof ExceptionTransfer)) {
      return DfaControlTransferValue.dispatch(state, interpreter, target, nextTraps);
    }
    state.emptyStack();
    TypeConstraint throwableType = ((ExceptionTransfer)target).getThrowable();
    List<DfaInstructionState> result = new ArrayList<>();
    for (var entry : myClauses.entrySet()) {
      ControlFlow.ControlFlowOffset jumpOffset = entry.getValue();
      CatchClauseDescriptor catchSection = entry.getKey();

      for (TypeConstraint caughtType : catchSection.constraints()) {
        TypeConstraint caught = Objects.requireNonNull(throwableType).meet(caughtType);
        if (caught != TypeConstraints.BOTTOM) {
          result.add(new DfaInstructionState(interpreter.getInstruction(jumpOffset.getInstructionOffset()),
                                             stateForCatchClause(state, interpreter, catchSection.parameter(), caught)));
        }

        TypeConstraint negated = caughtType.tryNegate();
        if (negated != null) {
          throwableType = throwableType.meet(negated);
          if (throwableType == TypeConstraints.BOTTOM) return result;
        }
      }
    }
    return ContainerUtil.concat(result, 
                                DfaControlTransferValue.dispatch(state, interpreter, new ExceptionTransfer(throwableType), nextTraps));
  }

  private static DfaMemoryState stateForCatchClause(DfaMemoryState state,
                                                    DataFlowInterpreter interpreter,
                                                    @Nullable VariableDescriptor param,
                                                    TypeConstraint constraint) {
    DfaMemoryState catchingCopy = state.createCopy();
    if (param != null) {
      DfaVariableValue value = interpreter.getFactory().getVarFactory().createVariableValue(param);
      catchingCopy.meetDfType(value, constraint.asDfType().meet(JavaDfaNullability.asDfType(DfaNullability.NOT_NULL)));
    }
    return catchingCopy;
  }

  @Override
  public PsiElement getAnchor() {
    return myStatement;
  }

  @Override
  public int [] getPossibleTargets() {
    Collection<? extends ControlFlow.ControlFlowOffset> values = myClauses.values();
    int[] result = new int[values.size()];
    int i = 0;
    for (ControlFlow.ControlFlowOffset value : values) {
      result[i++] = value.getInstructionOffset();
    }
    return result;
  }

  @Override
  public String toString() {
    return "TryCatch -> " + myClauses.values();
  }
}
