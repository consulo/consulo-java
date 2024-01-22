/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.java.language.psi.JavaTokenType;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author peter
 */
public class InstanceofInstruction extends BinopInstruction
{
	@Nullable
	private final PsiExpression myLeft;
	@jakarta.annotation.Nullable
	private final PsiType myCastType;

	public InstanceofInstruction(PsiExpression psiAnchor, @jakarta.annotation.Nullable PsiExpression left, @Nonnull PsiType castType)
	{
		super(JavaTokenType.INSTANCEOF_KEYWORD, psiAnchor, PsiType.BOOLEAN);
		myLeft = left;
		myCastType = castType;
	}

	/**
	 * Construct a class object instanceof check (e.g. from Class.isInstance call); castType is not known
	 *
	 * @param psiAnchor anchor call
	 */
	public InstanceofInstruction(PsiMethodCallExpression psiAnchor)
	{
		super(JavaTokenType.INSTANCEOF_KEYWORD, psiAnchor, PsiType.BOOLEAN);
		myLeft = null;
		myCastType = null;
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor)
	{
		return visitor.visitInstanceof(this, runner, stateBefore);
	}

	/**
	 * @return instanceof operand or null if it's not applicable
	 * (e.g. instruction is emitted when inlining Xyz.class::isInstance method reference)
	 */
	@jakarta.annotation.Nullable
	public PsiExpression getLeft()
	{
		return myLeft;
	}

	@jakarta.annotation.Nullable
	public PsiType getCastType()
	{
		return myCastType;
	}

	/**
	 * @return true if this instanceof instruction checks against Class object (e.g. Class.isInstance() call). In this case
	 * class object is located on the stack and cast type is not known
	 */
	public boolean isClassObjectCheck()
	{
		return myCastType == null;
	}
}
