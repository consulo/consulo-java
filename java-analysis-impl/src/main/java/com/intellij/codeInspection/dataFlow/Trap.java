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

package com.intellij.codeInspection.dataFlow;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;

/**
 * from kotlin
 */
public class Trap
{
	static class TryCatch extends Trap
	{
		private PsiTryStatement myTryStatement;
		private Map<PsiCatchSection, ControlFlow.ControlFlowOffset> myClauses;

		TryCatch(PsiTryStatement tryStatement, Map<PsiCatchSection, ControlFlow.ControlFlowOffset> clauses)
		{
			super(tryStatement);
			myTryStatement = tryStatement;
			myClauses = clauses;
		}

		public PsiTryStatement getTryStatement()
		{
			return myTryStatement;
		}

		@Nonnull
		public Map<PsiCatchSection, ControlFlow.ControlFlowOffset> getClauses()
		{
			return myClauses;
		}
	}

	static class TryFinally extends Trap
	{
		private PsiCodeBlock myFinallyBlock;
		private ControlFlow.ControlFlowOffset myJumpOffset;

		TryFinally(PsiCodeBlock finallyBlock, ControlFlow.ControlFlowOffset jumpOffset)
		{
			super(finallyBlock);
			myFinallyBlock = finallyBlock;
			myJumpOffset = jumpOffset;
		}

		public PsiCodeBlock getFinallyBlock()
		{
			return myFinallyBlock;
		}

		public ControlFlow.ControlFlowOffset getJumpOffset()
		{
			return myJumpOffset;
		}
	}

	static class InsideFinally extends Trap
	{
		InsideFinally(PsiCodeBlock finallyBlock)
		{
			super(finallyBlock);
		}
	}

	private final PsiElement myAnchor;

	public Trap(PsiElement anchor)
	{
		this.myAnchor = anchor;
	}

	@Nonnull
	public Collection<Integer> getPossibleTargets()
	{
		if(this instanceof TryCatch)
		{
			return ((TryCatch)this).getClauses().values().stream().map(ControlFlow.ControlFlowOffset::getInstructionOffset).collect(Collectors.toList());
		}
		else if(this instanceof TryFinally)
		{
			return Collections.singletonList(((TryFinally) this).getJumpOffset().getInstructionOffset());
		}
		return Collections.emptyList();
	}

	public PsiElement getAnchor()
	{
		return myAnchor;
	}
}
