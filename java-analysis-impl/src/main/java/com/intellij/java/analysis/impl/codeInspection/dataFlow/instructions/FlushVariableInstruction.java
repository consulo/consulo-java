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
import com.intellij.java.analysis.impl.codeInspection.dataFlow.value.DfaVariableValue;
import jakarta.annotation.Nonnull;

/**
 * Flush single variable
 */
public class FlushVariableInstruction extends Instruction
{
	private final
	@jakarta.annotation.Nonnull
	DfaVariableValue myVariable;

	/**
	 * @param variable variable to flush
	 */
	public FlushVariableInstruction(@jakarta.annotation.Nonnull DfaVariableValue variable)
	{
		myVariable = variable;
	}

	@Nonnull
	public DfaVariableValue getVariable()
	{
		return myVariable;
	}

	@Override
	public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor)
	{
		return visitor.visitFlushVariable(this, runner, stateBefore);
	}

	public String toString()
	{
		return "FLUSH " + myVariable.toString();
	}
}
