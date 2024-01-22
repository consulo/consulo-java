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

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * from kotlin
 */
public class InstructionTransfer implements TransferTarget
{
	private ControlFlow.ControlFlowOffset offset;
	private List<DfaVariableValue> toFlush;

	public InstructionTransfer(ControlFlow.ControlFlowOffset offset, List<DfaVariableValue> toFlush)
	{
		this.offset = offset;
		this.toFlush = toFlush;
	}

	@Override
	public List<DfaInstructionState> dispatch(DfaMemoryState state, DataFlowRunner runner)
	{
		for(DfaVariableValue toFlush : this.toFlush)
		{
			state.flushVariable(toFlush);
		}
		return List.of(new DfaInstructionState(runner.getInstruction(offset.getInstructionOffset()), state));
	}

	@Nonnull
	@Override
	public Collection<Integer> getPossibleTargets()
	{
		return List.of(offset.getInstructionOffset());
	}

	public ControlFlow.ControlFlowOffset getControlFlowOffset()
	{
		return offset;
	}

	public List<DfaVariableValue> getToFlush()
	{
		return toFlush;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}
		InstructionTransfer that = (InstructionTransfer) o;
		return Objects.equals(offset, that.offset) &&
				Objects.equals(toFlush, that.toFlush);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(offset, toFlush);
	}
}
