/*
 * Copyright 2013-2017 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.util.ObjectUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * from kotlin
 */
class ControlTransferHandler
{
	private DfaMemoryState state;
	private DataFlowRunner runner;
	private TransferTarget target;
	private FList<Trap> traps;

	private TypeConstraint throwableType;

	ControlTransferHandler(DfaMemoryState state, DataFlowRunner runner, DfaControlTransferValue transferValue)
	{
		this.state = state;
		this.runner = runner;
		this.target = transferValue.getTarget();
		this.traps = transferValue.getTraps();
	}

	@Nonnull
	public List<DfaInstructionState> dispatch()
	{
		Trap head = traps.getHead();

		traps = ObjectUtil.notNull(traps.getTail(), FList.emptyList());

		state.emptyStack();

		if(head != null)
		{
			return head.dispatch(this);
		}
		else
		{
			return target.dispatch(state, runner);
		}
	}

	public FList<Trap> getTraps()
	{
		return traps;
	}

	public DfaMemoryState getState()
	{
		return state;
	}

	public TransferTarget getTarget()
	{
		return target;
	}

	public DataFlowRunner getRunner()
	{
		return runner;
	}

	public List<DfaInstructionState> processCatches(@Nonnull TypeConstraint thrownValue, Map<PsiCatchSection, ControlFlow.ControlFlowOffset> catches)
	{
		List<DfaInstructionState> result = new ArrayList<>();

		for(Map.Entry<PsiCatchSection, ControlFlow.ControlFlowOffset> entry : catches.entrySet())
		{
			PsiCatchSection catchSection = entry.getKey();
			ControlFlow.ControlFlowOffset jumpOffset = entry.getValue();

			PsiParameter param = catchSection.getParameter();
			if(param == null)
			{
				continue;
			}

			if(throwableType == null)
			{
				throwableType = thrownValue;
			}

			for(TypeConstraint caughtType : allCaughtTypes(param))
			{
				TypeConstraint caught = thrownValue.meet(caughtType);

				if(caught != TypeConstraints.BOTTOM)
				{
					result.add(new DfaInstructionState(runner.getInstruction(jumpOffset.getInstructionOffset()), stateForCatchClause(param, caught)));
				}

				TypeConstraint negated = caughtType.tryNegate();
				if(negated != null)
				{
					throwableType = throwableType == null ? null : throwableType.meet(negated);
					if(throwableType == TypeConstraints.BOTTOM)
					{
						return result;
					}
				}
			}
		}

		return ContainerUtil.concat(result, dispatch());
	}

	@Nonnull
	private List<TypeConstraint> allCaughtTypes(PsiParameter param)
	{
		PsiType type = param.getType();
		List<PsiType> psiTypes;
		psiTypes = type instanceof PsiDisjunctionType ? ((PsiDisjunctionType) type).getDisjunctions() : List.of(type);
		return psiTypes.stream().map(TypeConstraints::instanceOf).collect(Collectors.toList());
	}

	@Nonnull
	private DfaMemoryState stateForCatchClause(PsiParameter param, TypeConstraint constraint)
	{
		DfaMemoryState catchingCopy = state.createCopy();
		DfaVariableValue value = runner.getFactory().getVarFactory().createVariableValue(param);

		catchingCopy.meetDfType(value, constraint.asDfType().meet(DfaNullability.NOT_NULL.asDfType()));
		return catchingCopy;
	}
}
