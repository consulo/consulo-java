// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import javax.annotation.Nonnull;

/**
 * Instruction to push a field qualified by the value on the stack
 */
public class UnwrapSpecialFieldInstruction extends EvalInstruction
{
	@Nonnull
	private final SpecialField mySpecialField;

	public UnwrapSpecialFieldInstruction(@Nonnull SpecialField specialField)
	{
		super(null, 1);
		mySpecialField = specialField;
	}

	@Override
	@Nonnull
	public DfaValue eval(@Nonnull DfaValueFactory factory, @Nonnull DfaMemoryState state, @Nonnull DfaValue  ... arguments)
	{
		return mySpecialField.createValue(factory, arguments[0]);
	}

	@Override
	public String toString()
	{
		return "UNWRAP " + mySpecialField;
	}
}
