// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.psi.PsiExpression;
import javax.annotation.Nonnull;

public class ResultOfInstruction extends Instruction implements ExpressionPushingInstruction
{
	@Nonnull
	private final PsiExpression myExpression;

	public ResultOfInstruction(@Nonnull PsiExpression expression)
	{
		myExpression = expression;
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor)
	{
		return visitor.visitResultOf(this, runner, stateBefore);
	}

	public String toString()
	{
		return "RESULT_OF " + myExpression.getText();
	}

	@Nonnull
	@Override
	public PsiExpression getExpression()
	{
		return myExpression;
	}
}
