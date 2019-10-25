// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.*;
import javax.annotation.Nonnull;

/**
 * Instruction to push a field qualified by the value on the stack
 */
public class UnwrapSpecialFieldInstruction extends Instruction
{
	@Nonnull
	private final SpecialField mySpecialField;

	public UnwrapSpecialFieldInstruction(@Nonnull SpecialField specialField)
	{
		mySpecialField = specialField;
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor)
	{
		return visitor.visitUnwrapField(this, runner, stateBefore);
	}

	@Nonnull
	public SpecialField getSpecialField()
	{
		return mySpecialField;
	}

	@Override
	public String toString()
	{
		return "UNWRAP " + mySpecialField;
	}
}
