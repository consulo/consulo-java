// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.java.language.psi.PsiType;
import jakarta.annotation.Nullable;

public class BoxingInstruction extends Instruction
{
	@Nullable
	private final PsiType myTargetType;

	public BoxingInstruction(@Nullable PsiType targetType)
	{
		myTargetType = targetType;
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor)
	{
		return visitor.visitBox(this, runner, stateBefore);
	}

	@Nullable
	public PsiType getTargetType()
	{
		return myTargetType;
	}

	@Override
	public String toString()
	{
		return "BOX";
	}
}
