// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import consulo.language.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.JavaDfaListener;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.MethodCallInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst.ThrowInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import consulo.language.dataFlow.interpreter.UnsatisfiedConditionProblem;
import consulo.language.dataFlow.ir.ControlFlow;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.ir.ReturnInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.DfaVariableValue;
import consulo.language.dataFlow.value.RelationType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.language.psi.PsiCall;
import com.intellij.java.language.psi.PsiCodeBlock;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.util.lang.ThreeState;
import consulo.util.collection.ContainerUtil;
import com.intellij.java.analysis.impl.codeInspection.ControlFlowUtils;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class ContractChecker {
  private static class ContractCheckListener implements JavaDfaListener {
    private final PsiMethod myMethod;
    private final StandardMethodContract myContract;
    private final boolean myOwnContract;
    private final Set<PsiElement> myViolations = new HashSet<>();
    private final Set<PsiElement> myNonViolations = new HashSet<>();
    private final Set<PsiElement> myFailures = new HashSet<>();
    private boolean myMayReturnNormally = false;

    ContractCheckListener(PsiMethod method, StandardMethodContract contract, boolean ownContract) {
      myMethod = method;
      myContract = contract;
      myOwnContract = ownContract;
    }

    @Override
    public void beforeValueReturn(DfaValue value,
                                  @Nullable PsiExpression expression,
                                  PsiElement context,
                                  DfaMemoryState state) {
      if (context != myMethod || expression == null || state.isEphemeral()) return;
      if (!myContract.getReturnValue().isValueCompatible(state, value)) {
        myViolations.add(expression);
      } else {
        myNonViolations.add(expression);
      }
    }

    @Override
    public void onCondition(UnsatisfiedConditionProblem problem,
                            DfaValue value,
                            ThreeState failed,
                            DfaMemoryState state) {
      if (problem instanceof ContractFailureProblem && failed != ThreeState.NO) {
        ContainerUtil.addIfNotNull(myFailures, ((ContractFailureProblem)problem).getAnchor());
      }
    }

    private Map<PsiElement, String> getErrors() {
      HashMap<PsiElement, String> errors = new HashMap<>();
      for (PsiElement element : myViolations) {
        if (!myNonViolations.contains(element)) {
          errors.put(element, JavaAnalysisBundle.message("inspection.contract.checker.contract.violated", myContract));
        }
      }

      if (!myContract.getReturnValue().isFail()) {
        if (myOwnContract && !myMayReturnNormally &&
            !(PsiUtil.canBeOverridden(myMethod) && ControlFlowUtils.methodAlwaysThrowsException(myMethod))) {
          for (PsiElement element : myFailures) {
            if (myContract.isTrivial()) {
              errors.put(element, JavaAnalysisBundle.message("inspection.contract.checker.method.always.fails.trivial", myContract));
            }
            else {
              errors.put(element, JavaAnalysisBundle.message("inspection.contract.checker.method.always.fails.nontrivial", myContract));
            }
          }
        }
      } else if (myFailures.isEmpty() && errors.isEmpty() && myMayReturnNormally) {
        PsiIdentifier nameIdentifier = myMethod.getNameIdentifier();
        errors.put(nameIdentifier != null ? nameIdentifier : myMethod,
                   JavaAnalysisBundle.message("inspection.contract.checker.no.exception.thrown", myContract));
      }

      return errors;
    }

    private static boolean weCannotInferAnythingAboutMethodReturnValue(MethodCallInstruction instruction) {
      PsiMethod target = instruction.getTargetMethod();
      return instruction.getContracts().isEmpty() && target != null && !target.isConstructor() && !NullableNotNullManager.isNotNull(target);
    }
  }

  static Map<PsiElement, String> checkContractClause(PsiMethod method, StandardMethodContract contract, boolean ownContract) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return Collections.emptyMap();

    DfaValueFactory factory = new DfaValueFactory(method.getProject());
    ControlFlow flow = ControlFlowAnalyzer.buildFlow(body, factory, true);
    if (flow == null) return Collections.emptyMap();
    ContractCheckListener interceptor = new ContractCheckListener(method, contract, ownContract);
    StandardDataFlowInterpreter interpreter = new StandardDataFlowInterpreter(flow, interceptor, true) {
      @Override
      protected DfaInstructionState [] acceptInstruction(DfaInstructionState instructionState) {
        Instruction instruction = instructionState.getInstruction();
        DfaMemoryState memState = instructionState.getMemoryState();
        if (instruction instanceof ThrowInstruction) {
          ContainerUtil.addIfNotNull(interceptor.myFailures, ((ThrowInstruction)instruction).getAnchor());
        }
        else if (instruction instanceof ReturnInstruction) {
          interceptor.myMayReturnNormally = true;
        }
        else if (instruction instanceof MethodCallInstruction) {
          PsiCall call = ((MethodCallInstruction)instruction).getCallExpression();
          if (!memState.isEphemeral() && call != null) {
            if (interceptor.myContract.getReturnValue().isFail()) {
              interceptor.myFailures.add(call);
              return DfaInstructionState.EMPTY_ARRAY;
            }
            if (ContractCheckListener.weCannotInferAnythingAboutMethodReturnValue((MethodCallInstruction)instruction)) {
              DfaInstructionState[] states = super.acceptInstruction(instructionState);
              for (DfaInstructionState state : states) {
                state.getMemoryState().markEphemeral();
              }
              return states;
            }
          }
        }
        return super.acceptInstruction(instructionState);
      }
    };
    PsiParameter[] parameters = method.getParameterList().getParameters();
    final DfaMemoryState initialState = DfaUtil.createStateWithEnabledAssertions(factory);
    for (int i = 0; i < contract.getParameterCount(); i++) {
      ValueConstraint constraint = contract.getParameterConstraint(i);
      DfaValue comparisonValue = constraint.getComparisonValue(factory);
      if (comparisonValue != null) {
        boolean negated = constraint.shouldUseNonEqComparison();
        DfaVariableValue dfaParam = PlainDescriptor.createVariableValue(factory, parameters[i]);
        initialState.applyCondition(dfaParam.cond(RelationType.equivalence(!negated), comparisonValue));
      }
    }
    interpreter.interpret(initialState);
    return interceptor.getErrors();
  }
}
