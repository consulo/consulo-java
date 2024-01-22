// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.java.language.psi.PsiExpression;
import jakarta.annotation.Nonnull;

import java.util.Objects;

/**
 * Instruction that does nothing but allows to attach an expression to the top-of-stack
 */
public class ResultOfInstruction extends EvalInstruction
{
	public ResultOfInstruction(@Nonnull PsiExpression expression)
	{
		super(expression, 1);
	}

	@Override
	public
	@Nonnull
	DfaValue eval(@jakarta.annotation.Nonnull DfaValueFactory factory, @Nonnull DfaMemoryState state, @Nonnull DfaValue ... arguments)
	{
		return arguments[0];
	}

	public String toString()
	{
		return "RESULT_OF " + getExpression().getText();
	}

	@jakarta.annotation.Nonnull
	@Override
	public PsiExpression getExpression()
	{
		return Objects.requireNonNull(super.getExpression());
	}
}
