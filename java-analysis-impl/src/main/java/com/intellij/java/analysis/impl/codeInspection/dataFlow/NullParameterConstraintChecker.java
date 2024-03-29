// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.language.codeInsight.Nullability;
import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.AssignInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifierListOwner;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.analysis.impl.psi.impl.search.JavaNullMethodArgumentUtil;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * This checker uses following idea:
 * On the checker's start mark all parameters with null-argument usages as violated (i.e. the method fails if parameter is null).
 * A parameter can be amnestied (excluded from violated) when one of following statements is true:
 * 1. If at least one successful method execution ({@link ReturnInstruction#isViaException()} == false)
 * doesn't require a not-null value for the parameter ({@link DfaMemoryState#isNotNull(DfaValue) == false});
 * OR
 * 2. If the parameter has a reassignment while one of any method execution.
 * <p>
 * All remaining violated parameters is required to be not-null for successful method execution.
 */
final class NullParameterConstraintChecker extends DataFlowRunner
{
	private final Set<PsiParameter> myPossiblyViolatedParameters;
	private final Set<PsiParameter> myUsedParameters;
	private final Set<PsiParameter> myParametersWithSuccessfulExecutionInNotNullState;

	private NullParameterConstraintChecker(Project project, Collection<PsiParameter> parameters)
	{
		super(project);
		myPossiblyViolatedParameters = new HashSet<>(parameters);
		myParametersWithSuccessfulExecutionInNotNullState = new HashSet<>();
		myUsedParameters = new HashSet<>();
	}

	@Nonnull
	static PsiParameter [] checkMethodParameters(PsiMethod method)
	{
		if(method.getBody() == null)
		{
			return PsiParameter.EMPTY_ARRAY;
		}

		final Collection<PsiParameter> nullableParameters = new SmartList<>();
		final PsiParameter[] parameters = method.getParameterList().getParameters();
		for(int index = 0; index < parameters.length; index++)
		{
			PsiParameter parameter = parameters[index];
			if(!(parameter.getType() instanceof PsiPrimitiveType) &&
					NullableNotNullManager.getNullability(parameter) == Nullability.UNKNOWN &&
					JavaNullMethodArgumentUtil.hasNullArgument(method, index))
			{
				nullableParameters.add(parameter);
			}
		}
		if(nullableParameters.isEmpty())
		{
			return PsiParameter.EMPTY_ARRAY;
		}

		NullParameterConstraintChecker checker = new NullParameterConstraintChecker(method.getProject(), nullableParameters);
		checker.analyzeMethod(method.getBody(), new StandardInstructionVisitor());

		return checker.myPossiblyViolatedParameters
				.stream()
				.filter(checker.myUsedParameters::contains)
				.filter(checker.myParametersWithSuccessfulExecutionInNotNullState::contains)
				.toArray(PsiParameter[]::new);
	}

	@Override
	@Nonnull
	protected DfaInstructionState  [] acceptInstruction(@Nonnull InstructionVisitor visitor, @Nonnull DfaInstructionState instructionState)
	{
		Instruction instruction = instructionState.getInstruction();
		if(instruction instanceof PushInstruction)
		{
			final DfaValue var = ((PushInstruction) instruction).getValue();
			if(var instanceof DfaVariableValue)
			{
				final PsiModifierListOwner psiVar = ((DfaVariableValue) var).getPsiVariable();
				if(psiVar instanceof PsiParameter)
				{
					myUsedParameters.add((PsiParameter) psiVar);
				}
			}
		}

		if(instruction instanceof AssignInstruction)
		{
			final DfaValue value = ((AssignInstruction) instruction).getAssignedValue();
			if(value instanceof DfaVariableValue)
			{
				final PsiModifierListOwner psiVariable = ((DfaVariableValue) value).getPsiVariable();
				if(psiVariable instanceof PsiParameter)
				{
					myPossiblyViolatedParameters.remove(psiVariable);
				}
			}
		}

		if(instruction instanceof ReturnInstruction && !((ReturnInstruction) instruction).isViaException())
		{
			DfaMemoryState memState = instructionState.getMemoryState();
			for(PsiParameter parameter : myPossiblyViolatedParameters.toArray(PsiParameter.EMPTY_ARRAY))
			{
				final DfaVariableValue dfaVar = getFactory().getVarFactory().createVariableValue(parameter);
				if(memState.isNotNull(dfaVar))
				{
					myParametersWithSuccessfulExecutionInNotNullState.add(parameter);
				}
				else
				{
					myPossiblyViolatedParameters.remove(parameter);
				}
			}
		}

		return super.acceptInstruction(visitor, instructionState);
	}

	@Nonnull
	@Override
	protected DfaMemoryState createMemoryState()
	{
		return new MyDfaMemoryState(getFactory());
	}

	private class MyDfaMemoryState extends DfaMemoryStateImpl
	{

		protected MyDfaMemoryState(DfaValueFactory factory)
		{
			super(factory);
			for(PsiParameter parameter : myPossiblyViolatedParameters)
			{
				recordVariableType(getFactory().getVarFactory().createVariableValue(parameter),
						DfaNullability.NULLABLE.asDfType());
			}
		}

		protected MyDfaMemoryState(MyDfaMemoryState toCopy)
		{
			super(toCopy);
		}

		@Override
		protected void flushVariable(@Nonnull DfaVariableValue variable, boolean shouldMarkFlushed)
		{
			final PsiModifierListOwner psi = variable.getPsiVariable();
			if(psi instanceof PsiParameter && myPossiblyViolatedParameters.contains(psi))
			{
				return;
			}
			super.flushVariable(variable, shouldMarkFlushed);
		}

		@Nonnull
		@Override
		public DfaMemoryStateImpl createCopy()
		{
			return new MyDfaMemoryState(this);
		}
	}
}
