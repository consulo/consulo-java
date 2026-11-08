// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.ContractValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaCallArguments;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaCallState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.MethodContract;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.MutationSignature;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraints;
import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaMethodReferenceReturnAnchor;
import consulo.language.dataFlow.ir.DfaInstructionState;
import consulo.language.dataFlow.ir.ExpressionPushingInstruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.types.DfType;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes;
import consulo.language.dataFlow.value.DfaValue;
import com.intellij.java.language.psi.JavaResolveResult;
import com.intellij.java.language.psi.LambdaUtil;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiEllipsisType;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodReferenceExpression;
import com.intellij.java.language.psi.PsiMethodReferenceUtil;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.java.analysis.impl.codeInspection.dataFlow.types.DfTypes.typedObject;
import static consulo.util.lang.ObjectUtil.tryCast;

/**
 * This instruction pops a top-of-stack value dereferencing it (so nullability warning might be issued if top-of-stack is nullable value).
 */
public class MethodReferenceInstruction extends ExpressionPushingInstruction {
  public MethodReferenceInstruction(PsiMethodReferenceExpression expression) {
    super(new JavaExpressionAnchor(expression));
  }

  @Override
  public DfaInstructionState[] accept(DataFlowInterpreter interpreter, DfaMemoryState stateBefore) {
    PsiMethodReferenceExpression expression = getMethodReference();
    DfaValue qualifier = stateBefore.pop();
    JavaDfaHelpers.dropLocality(qualifier, stateBefore);
    handleMethodReference(qualifier, interpreter, stateBefore);
    pushResult(interpreter, stateBefore, JavaDfaHelpers.getFunctionDfType(expression));
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public String toString() {
    return "METHOD_REF: " + getDfaAnchor();
  }
  
  public PsiMethodReferenceExpression getMethodReference() {
    return ((PsiMethodReferenceExpression)((JavaExpressionAnchor)Objects.requireNonNull(getDfaAnchor())).getExpression());
  }

  private void handleMethodReference(DfaValue qualifier, DataFlowInterpreter interpreter, DfaMemoryState state) {
    PsiMethodReferenceExpression methodRef = getMethodReference();
    PsiMethod sam = LambdaUtil.getFunctionalInterfaceMethod(methodRef);
    if (sam == null) return;
    JavaResolveResult resolveResult = methodRef.advancedResolve(false);
    PsiMethod method = tryCast(resolveResult.getElement(), PsiMethod.class);
    if (method == null) return;
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    DfaCallArguments callArguments = getMethodReferenceCallArguments(state, methodRef, qualifier, interpreter, sam, method, substitutor);
    if (isQualifierDereferenced(methodRef)) {
      CheckNotNullInstruction.dereference(interpreter, state, callArguments.getQualifier(),
                                          NullabilityProblemKind.callMethodRefNPE.problem(methodRef, null));
    }
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, null);
    PsiType returnType = substitutor.substitute(method.getReturnType());
    DfType dfType;
    if (method.isConstructor()) {
      PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        dfType = TypeConstraints.exactClass(containingClass).asDfType().meet(DfTypes.NOT_NULL_OBJECT);
      } else {
        dfType = DfTypes.NOT_NULL_OBJECT;
      }
    } else {
      dfType = typedObject(returnType, DfaPsiUtil.getElementNullabilityForRead(returnType, method));
    }
    DfaValue defaultResult = interpreter.getFactory().fromDfType(dfType);
    PsiType type = LambdaUtil.getFunctionalInterfaceReturnType(methodRef);
    if (!(type instanceof PsiPrimitiveType)) { // primitive type may be subject to boxing and handled separately
      Nullability expectedNullability = DfaPsiUtil.getTypeNullability(type);
      if (expectedNullability == Nullability.NOT_NULL) {
        CheckNotNullInstruction.checkNotNullable(interpreter, state, defaultResult,
                                                 NullabilityProblemKind.nullableFunctionReturn.problem(methodRef, null));
      }
    }
    if (contracts.isEmpty() || !JavaMethodContractUtil.isPure(method)) return;
    Set<DfaCallState> currentStates = Collections.singleton(new DfaCallState(state.createClosureState(), callArguments, defaultResult));
    JavaMethodReferenceReturnAnchor anchor = new JavaMethodReferenceReturnAnchor(methodRef);
    DfaValue[] args = callArguments.toArray();
    for (MethodContract contract : contracts) {
      Set<DfaMemoryState> results = new HashSet<>();
      currentStates = MethodCallInstruction.addContractResults(contract, currentStates, interpreter.getFactory(), results);
      for (DfaMemoryState result : results) {
        ContractValue.flushContractTempVariables(result);
        DfaValue value = result.pop();
        interpreter.getListener().beforePush(args, value, anchor, result);
        result.push(value);
      }
    }
    for (DfaCallState currentState: currentStates) {
      interpreter.getListener().beforePush(args, defaultResult, anchor, currentState.getMemoryState());
      currentState.getMemoryState().push(defaultResult);
      ContractValue.flushContractTempVariables(currentState.getMemoryState());
    }
  }

  static boolean isQualifierDereferenced(PsiMethodReferenceExpression methodRef) {
    if (methodRef.isConstructor()) return false;
    PsiElement target = methodRef.resolve();
    if (!(target instanceof PsiMethod)) return false;
    if (((PsiMethod)target).hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!PsiMethodReferenceUtil.isStaticallyReferenced(methodRef)) return false;
    return true;
  }

  private static DfaCallArguments getMethodReferenceCallArguments(DfaMemoryState state,
                                                                           PsiMethodReferenceExpression methodRef,
                                                                           DfaValue qualifier,
                                                                           DataFlowInterpreter interpreter,
                                                                           PsiMethod sam,
                                                                           PsiMethod method,
                                                                           PsiSubstitutor substitutor) {
    PsiParameter[] samParameters = sam.getParameterList().getParameters();
    boolean firstParameterIsQualifier = isQualifierDereferenced(methodRef);
    PsiParameter[] parameters = method.getParameterList().getParameters();
    DfaValue[] arguments = parameters.length == 0 ? DfaValue.EMPTY_ARRAY : new DfaValue[parameters.length];
    Arrays.fill(arguments, interpreter.getFactory().getUnknown());
    for (int i = 0; i < samParameters.length; i++) {
      DfaValue value = interpreter.getFactory().fromDfType(
        typedObject(substitutor.substitute(samParameters[i].getType()), DfaPsiUtil.getFunctionalParameterNullability(methodRef, i)));
      if (i == 0 && firstParameterIsQualifier) {
        qualifier = value;
      }
      else {
        int idx = i - (firstParameterIsQualifier ? 1 : 0);
        if (idx >= arguments.length) break;
        PsiType parameterType = parameters[idx].getType();
        if (!(parameterType instanceof PsiEllipsisType)) {
          Nullability nullability = DfaPsiUtil.getElementNullabilityForWrite(substitutor.substitute(parameterType), parameters[idx]);
          arguments[idx] = adaptMethodRefArgument(interpreter, state, value, methodRef, parameters[idx], nullability);
        }
      }
    }
    return new DfaCallArguments(qualifier, arguments, MutationSignature.fromMethod(method));
  }

  static DfaValue adaptMethodRefArgument(DataFlowInterpreter interpreter,
                                                  DfaMemoryState memState,
                                                  DfaValue arg,
                                                  PsiMethodReferenceExpression methodRef,
                                                  PsiParameter parameter,
                                                  @Nullable Nullability nullability) {
    if (TypeConversionUtil.isPrimitiveAndNotNull(parameter.getType())) {
      arg = CheckNotNullInstruction.dereference(interpreter, memState, arg,
                                                NullabilityProblemKind.unboxingMethodRefParameter.problem(methodRef, null));
      return DfaUtil.boxUnbox(arg, parameter.getType());
    }
    if (nullability == Nullability.NOT_NULL) {
      return CheckNotNullInstruction.dereference(interpreter, memState, arg,
                                                 NullabilityProblemKind.passingToNotNullMethodRefParameter.problem(methodRef, null));
    }
    if (nullability == Nullability.UNKNOWN) {
      CheckNotNullInstruction.checkNotNullable(interpreter, memState, arg,
                                               NullabilityProblemKind.passingToNonAnnotatedMethodRefParameter.problem(methodRef, null));
    }
    return arg;
  }
}
