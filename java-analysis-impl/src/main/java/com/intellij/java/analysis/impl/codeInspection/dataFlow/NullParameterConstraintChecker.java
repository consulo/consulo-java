// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import consulo.language.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.AssignInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import consulo.language.dataFlow.interpreter.DfaListener;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.ir.PushInstruction;
import consulo.language.dataFlow.ir.ReturnInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.analysis.impl.psi.impl.search.JavaNullMethodArgumentUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * This checker uses following idea:
 * On the checker's start mark all parameters with null-argument usages as violated (i.e. the method fails if parameter is null).
 * A parameter can be amnestied (excluded from violated) when one of following statements is true:
 * 1. If at least one successful method execution ({@link ReturnInstruction} visited)
 * doesn't require a not-null value for the parameter
 * OR
 * 2. If the parameter has a reassignment while one of any method execution.
 * <p>
 * All remaining violated parameters is required to be not-null for successful method execution.
 */
final class NullParameterConstraintChecker {
  static PsiParameter [] checkMethodParameters(PsiMethod method) {
    if (method.getBody() == null) return PsiParameter.EMPTY_ARRAY;

    final Set<PsiParameter> nullableParameters = new HashSet<>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int index = 0; index < parameters.length; index++) {
      PsiParameter parameter = parameters[index];
      if (!(parameter.getType() instanceof PsiPrimitiveType) &&
          NullableNotNullManager.getNullability(parameter) == Nullability.UNKNOWN &&
          JavaNullMethodArgumentUtil.hasNullArgument(method, index)) {
        nullableParameters.add(parameter);
      }
    }
    if (nullableParameters.isEmpty()) return PsiParameter.EMPTY_ARRAY;

    DfaValueFactory factory = new DfaValueFactory(method.getProject());
    ControlFlow flow = ControlFlowAnalyzer.buildFlow(method.getBody(), factory, true);
    if (flow == null) return PsiParameter.EMPTY_ARRAY;
    var interpreter = new NullParameterCheckerInterpreter(flow, nullableParameters);
    flow.keepVariables(desc -> desc.getPsiElement() instanceof PsiParameter psi && nullableParameters.contains(psi));
    DfaMemoryState state = new JvmDfaMemoryStateImpl(factory);
    for (PsiParameter parameter : nullableParameters) {
      state.applyCondition(PlainDescriptor.createVariableValue(factory, parameter).eq(DfaNullability.NULLABLE.asDfType()));
    }

    interpreter.interpret(state);

    return interpreter.myPossiblyViolatedParameters
      .stream()
      .filter(interpreter.myUsedParameters::contains)
      .filter(interpreter.myParametersWithSuccessfulExecutionInNotNullState::contains)
      .toArray(PsiParameter[]::new);
  }

  private static class NullParameterCheckerInterpreter extends StandardDataFlowInterpreter {
    final Set<PsiParameter> myPossiblyViolatedParameters;
    final Set<PsiParameter> myUsedParameters;
    final Set<PsiParameter> myParametersWithSuccessfulExecutionInNotNullState;

    private NullParameterCheckerInterpreter(ControlFlow flow, Set<PsiParameter> nullableParameters) {
      super(flow, DfaListener.EMPTY);
      myPossiblyViolatedParameters = nullableParameters;
      myUsedParameters = new HashSet<>();
      myParametersWithSuccessfulExecutionInNotNullState = new HashSet<>();
    }

    @Override
    protected DfaInstructionState [] acceptInstruction(DfaInstructionState instructionState) {
      Instruction instruction = instructionState.getInstruction();
      if (instruction instanceof PushInstruction) {
        final DfaValue var = ((PushInstruction)instruction).getValue();
        if (var instanceof DfaVariableValue) {
          final PsiElement psiVar = ((DfaVariableValue)var).getPsiVariable();
          if (psiVar instanceof PsiParameter) {
            myUsedParameters.add((PsiParameter)psiVar);
          }
        }
      }

      if (instruction instanceof AssignInstruction) {
        final DfaValue value = ((AssignInstruction)instruction).getAssignedValue();
        if (value instanceof DfaVariableValue) {
          final PsiElement psiVariable = ((DfaVariableValue)value).getPsiVariable();
          if (psiVariable instanceof PsiParameter) {
            myPossiblyViolatedParameters.remove(psiVariable);
          }
        }
      }

      if (instruction instanceof ReturnInstruction) {
        DfaMemoryState memState = instructionState.getMemoryState();
        for (PsiParameter parameter : myPossiblyViolatedParameters.toArray(PsiParameter.EMPTY_ARRAY)) {
          final DfaVariableValue dfaVar = PlainDescriptor.createVariableValue(getFactory(), parameter);
          if (!memState.getDfType(dfaVar).isSuperType(DfTypes.NULL)) {
            myParametersWithSuccessfulExecutionInNotNullState.add(parameter);
          }
          else {
            myPossiblyViolatedParameters.remove(parameter);
          }
        }
      }

      return super.acceptInstruction(instructionState);
    }
  }
}
