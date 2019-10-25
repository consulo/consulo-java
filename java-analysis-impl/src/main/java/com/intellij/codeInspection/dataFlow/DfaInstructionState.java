// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import javax.annotation.Nonnull;

public class DfaInstructionState implements Comparable<DfaInstructionState>
{
	public static final DfaInstructionState[] EMPTY_ARRAY = new DfaInstructionState[0];
	private final DfaMemoryState myBeforeMemoryState;
	private final Instruction myInstruction;

	public DfaInstructionState(@Nonnull Instruction myInstruction, @Nonnull DfaMemoryState myBeforeMemoryState)
	{
		this.myBeforeMemoryState = myBeforeMemoryState;
		this.myInstruction = myInstruction;
	}

	@Nonnull
	public Instruction getInstruction()
	{
		return myInstruction;
	}

	@Nonnull
	public DfaMemoryState getMemoryState()
	{
		return myBeforeMemoryState;
	}

	public String toString()
	{
		return getInstruction().getIndex() + " " + getInstruction() + ":   " + getMemoryState().toString();
	}

	@Override
	public int compareTo(@Nonnull DfaInstructionState o)
	{
		return Integer.compare(myInstruction.getIndex(), o.myInstruction.getIndex());
	}
}