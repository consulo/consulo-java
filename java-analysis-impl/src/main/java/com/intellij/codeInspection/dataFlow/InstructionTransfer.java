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

import java.util.List;

import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;

/**
 * from kotlin
 */
public class InstructionTransfer implements TransferTarget
{
	private ControlFlow.ControlFlowOffset myControlFlowOffset;
	private List<DfaVariableValue> myToFlush;

	public InstructionTransfer(ControlFlow.ControlFlowOffset controlFlowOffset, List<DfaVariableValue> toFlush)
	{
		myControlFlowOffset = controlFlowOffset;
		myToFlush = toFlush;
	}

	public ControlFlow.ControlFlowOffset getControlFlowOffset()
	{
		return myControlFlowOffset;
	}

	public List<DfaVariableValue> getToFlush()
	{
		return myToFlush;
	}
}
