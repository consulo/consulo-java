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

import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;

/**
 * @author max
 */
public class FieldReferenceInstruction extends Instruction
{
	private final PsiExpression myExpression;
	@Nullable
	private final String mySyntheticFieldName;

	public FieldReferenceInstruction(@Nonnull PsiExpression expression, @javax.annotation.Nullable @NonNls String syntheticFieldName)
	{
		myExpression = expression;
		mySyntheticFieldName = syntheticFieldName;
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor)
	{
		return visitor.visitFieldReference(this, runner, stateBefore);
	}

	public String toString()
	{
		return "FIELD_REFERENCE: " + myExpression.getText();
	}

	@Nonnull
	public PsiExpression getExpression()
	{
		return myExpression;
	}

	@Nullable
	public PsiExpression getElementToAssert()
	{
		if(mySyntheticFieldName != null)
		{
			return myExpression;
		}
		return myExpression instanceof PsiArrayAccessExpression ? ((PsiArrayAccessExpression) myExpression).getArrayExpression() : (
				(PsiReferenceExpression) myExpression).getQualifierExpression();
	}
}
