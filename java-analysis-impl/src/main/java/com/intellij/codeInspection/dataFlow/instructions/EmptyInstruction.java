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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:48:42 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import javax.annotation.Nullable;
import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.psi.PsiElement;


public class EmptyInstruction extends Instruction
{
	@Nullable
	private final PsiElement myAnchor;

	public EmptyInstruction(@javax.annotation.Nullable PsiElement anchor)
	{
		myAnchor = anchor;
	}

	@javax.annotation.Nullable
	public PsiElement getAnchor()
	{
		return myAnchor;
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor)
	{
		return visitor.visitEmptyInstruction(this, runner, stateBefore);
	}

	public String toString()
	{
		return "EMPTY";
	}
}
