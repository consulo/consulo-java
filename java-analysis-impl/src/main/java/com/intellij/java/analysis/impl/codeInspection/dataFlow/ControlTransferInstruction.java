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

import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.Instruction;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Instruction which performs complex control transfer (handling exception; processing finally blocks; exiting inlined lambda, etc.)
 * <p>
 * from kotlin
 */
public class ControlTransferInstruction extends Instruction
{
	@jakarta.annotation.Nonnull
	private DfaControlTransferValue transfer;

	public ControlTransferInstruction(@jakarta.annotation.Nonnull DfaControlTransferValue transfer)
	{
		this.transfer = transfer;
		this.transfer.getTraps().forEach(trap -> trap.link(this));
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState state, InstructionVisitor visitor)
	{
		return visitor.visitControlTransfer(this, runner, state);
	}

	@jakarta.annotation.Nonnull
	public DfaControlTransferValue getTransfer()
	{
		return transfer;
	}

	@Nonnull
	public List<Integer> getPossibleTargetIndices()
	{
		List<Integer> trapPossibleTargets = transfer.getTraps().stream().flatMap(trap -> trap.getPossibleTargets().stream()).collect(Collectors.toList());

		return ContainerUtil.concat(trapPossibleTargets, new ArrayList<>(transfer.getTarget().getPossibleTargets()));
	}

	@Override
	public String toString()
	{
		return "TRANSFER " + transfer + " [targets " + getPossibleTargetIndices() + "]";
	}
}
