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

package com.intellij.codeInspection.dataFlow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiDisjunctionType;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;

/**
 * from kotlin
 */
class ControlTransferHandler
{
	private DfaMemoryState myState;
	private DataFlowRunner myRunner;
	private TransferTarget myTarget;

	@Nullable
	private DfaVariableState throwableState;

	ControlTransferHandler(DfaMemoryState state, DataFlowRunner runner, TransferTarget target)
	{
		myState = state;
		myRunner = runner;
		myTarget = target;
	}

	public List<DfaInstructionState> iteration(FList<Trap> traps)
	{
		Trap head = traps.getHead();
		FList<Trap> tail = traps.getTail();
		if(head == null)
		{
			return transferToTarget();
		}
		else if(head instanceof Trap.TryCatch)
		{
			if(myTarget instanceof ExceptionTransfer)
			{
				return processCatches((Trap.TryCatch) head, ((ExceptionTransfer) myTarget).getThrowable(), tail);
			}
			else
			{
				return iteration(tail);
			}
		}
		else if(head instanceof Trap.TryFinally)
		{
			return gotoFinally(((Trap.TryFinally) head).getJumpOffset().getInstructionOffset(), tail);
		}
		else if(head instanceof Trap.InsideFinally)
		{
			return leaveFinally(tail);
		}
		throw new UnsupportedOperationException();
	}

	@NotNull
	private List<DfaInstructionState> processCatches(Trap.TryCatch tryCatch, DfaValue thrownValue, FList<Trap> traps)
	{
		List<DfaInstructionState> result = new ArrayList<>();
		for(Map.Entry<PsiCatchSection, ControlFlow.ControlFlowOffset> entry : tryCatch.getClauses().entrySet())
		{
			PsiCatchSection catchSection = entry.getKey();
			ControlFlow.ControlFlowOffset jumpOffset = entry.getValue();

			PsiParameter param = catchSection.getParameter();
			if(param == null)
			{
				continue;
			}

			if(throwableState == null)
			{
				throwableState = initVariableState(param, thrownValue);
			}

			for(DfaTypeValue caughtType : allCaughtTypes(param))
			{
				DfaVariableState varState = throwableState.withInstanceofValue(caughtType);
				if(varState != null)
				{
					result.add(new DfaInstructionState(myRunner.getInstruction(jumpOffset.getInstructionOffset()), stateForCatchClause(param, varState)));
				}

				throwableState = throwableState.withNotInstanceofValue(caughtType);

				if(throwableState == null)
				{
					return result;
				}
			}
		}
		return ContainerUtil.concat(result, iteration(traps));
	}

	private DfaMemoryState stateForCatchClause(PsiParameter param, DfaVariableState varState)
	{
		DfaMemoryStateImpl catchingCopy = (DfaMemoryStateImpl) myState.createCopy();
		catchingCopy.setVariableState(catchingCopy.getFactory().getVarFactory().createVariableValue(param, false), varState);
		return catchingCopy;
	}

	@NotNull
	private List<DfaTypeValue> allCaughtTypes(PsiParameter param)
	{
		List<PsiType> psiTypes;
		PsiType type = param.getType();
		if(type instanceof PsiDisjunctionType)
		{
			psiTypes = ((PsiDisjunctionType) type).getDisjunctions();
		}
		else
		{
			psiTypes = Collections.singletonList(type);
		}
		List<DfaValue> result = psiTypes.stream().map(it -> myRunner.getFactory().createTypeValue(it, Nullness.NOT_NULL)).collect(Collectors.toList());
		return ContainerUtil.<DfaValue, DfaTypeValue>mapNotNull(result, dfaValue -> dfaValue instanceof DfaTypeValue ? (DfaTypeValue) dfaValue : null);
	}

	@NotNull
	private DfaVariableState initVariableState(PsiParameter param, DfaValue throwable)
	{
		DfaVariableValue sampleVar = ((DfaMemoryStateImpl) myState).getFactory().getVarFactory().createVariableValue(param, false);
		DfaVariableState varState = ((DfaMemoryStateImpl) myState).createVariableState(sampleVar).withFact(DfaFactType.CAN_BE_NULL, false);
		if(throwable instanceof DfaTypeValue)
		{
			return Objects.requireNonNull(varState.withInstanceofValue((DfaTypeValue) throwable));
		}
		else
		{
			return varState;
		}
	}

	@NotNull
	private List<DfaInstructionState> gotoFinally(int offset, FList<Trap> traps)
	{
		myState.push(myRunner.getFactory().controlTransfer(myTarget, traps));
		return Collections.singletonList(new DfaInstructionState(myRunner.getInstruction(offset), myState));
	}

	@NotNull
	private List<DfaInstructionState> leaveFinally(FList<Trap> traps)
	{
		DfaControlTransferValue unused = (DfaControlTransferValue) myState.pop();
		return iteration(traps);
	}

	@NotNull
	private List<DfaInstructionState> transferToTarget()
	{
		if(myTarget instanceof InstructionTransfer)
		{
			for(DfaVariableValue value : ((InstructionTransfer) myTarget).getToFlush())
			{
				myState.flushVariable(value);
			}
			return Collections.singletonList(new DfaInstructionState(myRunner.getInstruction(((InstructionTransfer) myTarget).getControlFlowOffset().getInstructionOffset()), myState));
		}
		return Collections.emptyList();
	}
}
