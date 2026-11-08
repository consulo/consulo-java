// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.java.inst;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.TypeConstraint;
import consulo.language.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems.ArrayIndexProblem;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.jvm.problems.ArrayStoreProblem;
import consulo.language.dataFlow.ir.Instruction;
import consulo.language.dataFlow.memory.DfaMemoryState;
import consulo.language.dataFlow.types.DfType;
import consulo.language.dataFlow.value.DfaControlTransferValue;
import consulo.language.dataFlow.value.DfaValue;
import consulo.language.dataFlow.value.DfaValueFactory;
import consulo.language.dataFlow.value.VariableDescriptor;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiArrayAccessExpression;
import com.intellij.java.language.psi.PsiAssignmentExpression;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.util.PsiTreeUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.ThreeState;
import org.jspecify.annotations.Nullable;

/**
 * Java-specific array store instruction. Additionally, it checks for assignability.
 */
public class JavaArrayStoreInstruction extends ArrayStoreInstruction {
  private final PsiArrayAccessExpression myExpression;
  private final @Nullable PsiExpression myValueExpression;

  public JavaArrayStoreInstruction(PsiArrayAccessExpression expression,
                                   @Nullable PsiExpression valueExpression,
                                   @Nullable DfaControlTransferValue outOfBoundsTransfer,
                                   @Nullable VariableDescriptor staticVariable) {
    super(createAnchor(expression), new ArrayIndexProblem(expression), outOfBoundsTransfer, staticVariable);
    myExpression = expression;
    myValueExpression = valueExpression;
  }

  private static @Nullable JavaExpressionAnchor createAnchor(PsiArrayAccessExpression expression) {
    var assignment = ObjectUtil.tryCast(PsiUtil.skipParenthesizedExprUp(expression.getParent()), PsiAssignmentExpression.class);
    return assignment == null ? null : new JavaExpressionAnchor(assignment);
  }

  @Override
  public Instruction bindToFactory(DfaValueFactory factory) {
    DfaControlTransferValue transfer = myOutOfBoundsTransfer == null ? null : myOutOfBoundsTransfer.bindToFactory(factory);
    return new JavaArrayStoreInstruction(myExpression, myValueExpression, transfer, myStaticVariable);
  }

  @Override
  protected void checkArrayElementAssignability(DataFlowInterpreter interpreter,
                                                DfaMemoryState memState,
                                                DfaValue dfaSource,
                                                DfaValue qualifier) {
    if (myValueExpression == null) return;
    PsiType rCodeType = myValueExpression.getType();
    PsiType lCodeType = myExpression.getType();
    // If types known from source are not convertible, a compilation error is displayed, additional warning is unnecessary
    if (rCodeType == null || lCodeType == null || !TypeConversionUtil.areTypesConvertible(rCodeType, lCodeType)) return;
    DfType toType = TypeConstraint.fromDfType(memState.getDfType(qualifier)).getArrayComponentType();
    if (toType == DfType.BOTTOM) return;
    DfType fromType = memState.getDfType(dfaSource);
    DfType meet = fromType.meet(toType);
    Project project = interpreter.getFactory().getProject();
    PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(myValueExpression, PsiAssignmentExpression.class);
    PsiType psiFromType = TypeConstraint.fromDfType(fromType).getPsiType(project);
    PsiType psiToType = TypeConstraint.fromDfType(toType).getPsiType(project);
    if (assignmentExpression == null || psiFromType == null || psiToType == null) return;
    interpreter.getListener().onCondition(new ArrayStoreProblem(assignmentExpression, psiFromType, psiToType), dfaSource,
                                     meet == DfType.BOTTOM ? ThreeState.YES : ThreeState.UNSURE, memState);
  }
}
