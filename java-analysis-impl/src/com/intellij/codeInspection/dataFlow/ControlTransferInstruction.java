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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;

/**
 * from kotlin
 */
public class ControlTransferInstruction extends Instruction
{
	@Nullable
	private DfaControlTransferValue myTransfer;

	public ControlTransferInstruction(@Nullable DfaControlTransferValue transfer)
	{
		myTransfer = transfer;
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState state, InstructionVisitor visitor)
	{
		DfaControlTransferValue transferValue = myTransfer == null ? (DfaControlTransferValue) state.pop() : myTransfer;

		List<DfaInstructionState> iteration = new ControlTransferHandler(state, runner, transferValue.getTarget()).iteration(transferValue.getTraps());
		return iteration.toArray(new DfaInstructionState[iteration.size()]);
	}

	@Nullable
	public DfaControlTransferValue getTransfer()
	{
		return myTransfer;
	}

	@Override
	public String toString()
	{
		return myTransfer == null ? "null" : myTransfer.toString();
	}

	@NotNull
	public List<Integer> getPossibleTargetIndices()
	{
		if(myTransfer == null)
		{
			return Collections.emptyList();
		}

		List<Integer> result = new ArrayList<>(myTransfer.getTraps().stream().flatMap(trap -> trap.getPossibleTargets().stream()).collect(Collectors.toList()));
		if(myTransfer.getTarget() instanceof InstructionTransfer)
		{
			result.add(((InstructionTransfer) myTransfer.getTarget()).getControlFlowOffset().getInstructionOffset());
		}
		return result;
	}

	@NotNull
	public Collection<? extends Instruction> getPossibleTargetInstructions(Instruction[] allInstruction)
	{
		return getPossibleTargetIndices().stream().map(it -> allInstruction[it]).collect(Collectors.toList());
	}
}
