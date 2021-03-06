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

import java.util.Set;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author peter
 */
public class FinishElementInstruction extends Instruction
{
	private final Set<DfaVariableValue> myVarsToFlush = ContainerUtil.newHashSet();
	private final PsiElement myElement;

	public FinishElementInstruction(PsiElement element)
	{
		myElement = element;
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState state, InstructionVisitor visitor)
	{
		if(!myVarsToFlush.isEmpty())
		{
			for(DfaVariableValue value : myVarsToFlush)
			{
				state.flushVariable(value);
			}
		}
		return nextInstruction(runner, state);
	}

	@Override
	public String toString()
	{
		return "Finish " + myElement + "; flushing " + myVarsToFlush;
	}

	public Set<DfaVariableValue> getVarsToFlush()
	{
		return myVarsToFlush;
	}
}
