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
package com.intellij.codeInspection.dataFlow.instructions;

import javax.annotation.Nonnull;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiArrayAccessExpression;

/**
 * @author Tagir Valeev
 */
public class ArrayAccessInstruction extends Instruction
{
	private final
	@Nonnull
	DfaValue myValue;
	private final
	@Nonnull
	PsiArrayAccessExpression myExpression;

	public ArrayAccessInstruction(@Nonnull DfaValue value, @Nonnull PsiArrayAccessExpression expression)
	{
		myValue = value;
		myExpression = expression;
	}

	@Nonnull
	public DfaValue getValue()
	{
		return myValue;
	}

	@Nonnull
	public PsiArrayAccessExpression getExpression()
	{
		return myExpression;
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor)
	{
		return visitor.visitArrayAccess(this, runner, stateBefore);
	}

	@Override
	public String toString()
	{
		return "ARRAY_ACCESS " + myExpression.getText();
	}
}
