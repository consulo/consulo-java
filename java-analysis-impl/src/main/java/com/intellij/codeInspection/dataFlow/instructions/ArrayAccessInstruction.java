/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiArrayAccessExpression;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Objects;

public class ArrayAccessInstruction extends ExpressionPushingInstruction<PsiArrayAccessExpression>
{
	private final
	@Nonnull
	DfaValue myValue;
	private final
	@Nullable
	DfaControlTransferValue myTransferValue;

	public ArrayAccessInstruction(@Nonnull DfaValue value,
								  @Nonnull PsiArrayAccessExpression expression,
								  @Nullable DfaControlTransferValue transferValue)
	{
		super(expression);
		myValue = value;
		myTransferValue = transferValue;
	}

	@Nullable
	public DfaControlTransferValue getOutOfBoundsExceptionTransfer()
	{
		return myTransferValue;
	}

	@Nonnull
	public DfaValue getValue()
	{
		return myValue;
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor)
	{
		return visitor.visitArrayAccess(this, runner, stateBefore);
	}

	@Override
	public String toString()
	{
		return "ARRAY_ACCESS " + Objects.requireNonNull(getExpression()).getText();
	}
}
