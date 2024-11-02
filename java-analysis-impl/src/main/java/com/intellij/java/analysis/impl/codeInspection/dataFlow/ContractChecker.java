// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.RelationType;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import consulo.java.analysis.localize.JavaAnalysisLocalize;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author peter
 */
public final class ContractChecker {
    private static class ContractCheckerVisitor extends StandardInstructionVisitor {
        private final PsiMethod myMethod;
        private final StandardMethodContract myContract;
        private final boolean myOwnContract;
        private final Set<PsiElement> myViolations = new HashSet<>();
        private final Set<PsiElement> myNonViolations = new HashSet<>();
        private final Set<PsiElement> myFailures = new HashSet<>();
        private boolean myMayReturnNormally = false;

        ContractCheckerVisitor(PsiMethod method, StandardMethodContract contract, boolean ownContract) {
            super(true);
            myMethod = method;
            myContract = contract;
            myOwnContract = ownContract;
        }

        @Override
        protected void checkReturnValue(
            @Nonnull DfaValue value,
            @Nonnull PsiExpression expression,
            @Nonnull PsiParameterListOwner context,
            @Nonnull DfaMemoryState state
        ) {
            if (context != myMethod || state.isEphemeral()) {
                return;
            }
            if (!myContract.getReturnValue().isValueCompatible(state, value)) {
                myViolations.add(expression);
            }
            else {
                myNonViolations.add(expression);
            }
        }

        @Override
        public DfaInstructionState[] visitMethodCall(
            MethodCallInstruction instruction,
            DataFlowRunner runner,
            DfaMemoryState memState
        ) {
            PsiCall call = instruction.getCallExpression();
            if (!memState.isEphemeral() && call != null) {
                if (myContract.getReturnValue().isFail()) {
                    myFailures.add(call);
                    return DfaInstructionState.EMPTY_ARRAY;
                }
                if (weCannotInferAnythingAboutMethodReturnValue(instruction)) {
                    DfaInstructionState[] states = super.visitMethodCall(instruction, runner, memState);
                    for (DfaInstructionState state : states) {
                        state.getMemoryState().markEphemeral();
                    }
                    return states;
                }
            }
            return super.visitMethodCall(instruction, runner, memState);
        }

        @Override
        @Nonnull
        public DfaInstructionState[] visitControlTransfer(
            @Nonnull ControlTransferInstruction instruction,
            @Nonnull DataFlowRunner runner,
            @Nonnull DfaMemoryState state
        ) {
            if (instruction instanceof ReturnInstruction && ((ReturnInstruction)instruction).isViaException()) {
                ContainerUtil.addIfNotNull(myFailures, ((ReturnInstruction)instruction).getAnchor());
            }
            else {
                myMayReturnNormally = true;
            }
            return super.visitControlTransfer(instruction, runner, state);
        }

        private Map<PsiElement, String> getErrors() {
            HashMap<PsiElement, String> errors = new HashMap<>();
            for (PsiElement element : myViolations) {
                if (!myNonViolations.contains(element)) {
                    errors.put(element, JavaAnalysisLocalize.inspectionContractCheckerContractViolated(myContract).get());
                }
            }

            if (!myContract.getReturnValue().isFail()) {
                if (myOwnContract && !myMayReturnNormally &&
                    !(PsiUtil.canBeOverridden(myMethod) && ControlFlowUtils.methodAlwaysThrowsException(myMethod))) {
                    for (PsiElement element : myFailures) {
                        if (myContract.isTrivial()) {
                            errors.put(
                                element,
                                JavaAnalysisLocalize.inspectionContractCheckerMethodAlwaysFailsTrivial(myContract).get()
                            );
                        }
                        else {
                            errors.put(
                                element,
                                JavaAnalysisLocalize.inspectionContractCheckerMethodAlwaysFailsNontrivial(myContract).get()
                            );
                        }
                    }
                }
            }
            else if (myFailures.isEmpty() && errors.isEmpty() && myMayReturnNormally) {
                PsiIdentifier nameIdentifier = myMethod.getNameIdentifier();
                errors.put(
                    nameIdentifier != null ? nameIdentifier : myMethod,
                    JavaAnalysisLocalize.inspectionContractCheckerNoExceptionThrown(myContract).get()
                );
            }

            return errors;
        }

        private static boolean weCannotInferAnythingAboutMethodReturnValue(MethodCallInstruction instruction) {
            PsiMethod target = instruction.getTargetMethod();
            return instruction.getContracts().isEmpty()
                && target != null
                && !target.isConstructor()
                && !NullableNotNullManager.isNotNull(target);
        }
    }

    public static Map<PsiElement, String> checkContractClause(PsiMethod method, StandardMethodContract contract, boolean ownContract) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return Collections.emptyMap();
        }

        DataFlowRunner runner = new DataFlowRunner(method.getProject(), null);

        PsiParameter[] parameters = method.getParameterList().getParameters();
        final DfaMemoryState initialState = runner.createMemoryState();
        final DfaValueFactory factory = runner.getFactory();
        for (int i = 0; i < contract.getParameterCount(); i++) {
            ValueConstraint constraint = contract.getParameterConstraint(i);
            DfaValue comparisonValue = constraint.getComparisonValue(factory);
            if (comparisonValue != null) {
                boolean negated = constraint.shouldUseNonEqComparison();
                DfaVariableValue dfaParam = factory.getVarFactory().createVariableValue(parameters[i]);
                initialState.applyCondition(dfaParam.cond(RelationType.equivalence(!negated), comparisonValue));
            }
        }

        ContractCheckerVisitor visitor = new ContractCheckerVisitor(method, contract, ownContract);
        runner.analyzeMethod(body, visitor, Collections.singletonList(initialState));
        return visitor.getErrors();
    }
}
