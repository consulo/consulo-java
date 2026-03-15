// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.SpecialField;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;

/**
 * Instruction to push a field qualified by the value on the stack
 */
public class UnwrapSpecialFieldInstruction extends EvalInstruction
{
	private final SpecialField mySpecialField;

	public UnwrapSpecialFieldInstruction(SpecialField specialField)
	{
		super(null, 1);
		mySpecialField = specialField;
	}

	@Override
	public DfaValue eval(DfaValueFactory factory, DfaMemoryState state, DfaValue  ... arguments)
	{
		return mySpecialField.createValue(factory, arguments[0]);
	}

	@Override
	public String toString()
	{
		return "UNWRAP " + mySpecialField;
	}
}
