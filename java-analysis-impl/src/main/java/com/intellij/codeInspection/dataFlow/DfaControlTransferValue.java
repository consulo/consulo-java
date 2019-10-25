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

import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.util.containers.FList;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * from kotlin
 */
public class DfaControlTransferValue extends DfaValue
{
	private TransferTarget myTarget;
	@Nonnull
	private FList<Trap> myTraps;

	public DfaControlTransferValue(DfaValueFactory factory, TransferTarget target, @Nonnull FList<Trap> traps)
	{
		super(factory);
		myTarget = target;
		myTraps = traps;
	}

	public List<DfaInstructionState> dispatch(DfaMemoryState state, DataFlowRunner runner)
	{
		return new ControlTransferHandler(state, runner, this).dispatch();
	}

	public TransferTarget getTarget()
	{
		return myTarget;
	}

	@Nonnull
	public FList<Trap> getTraps()
	{
		return myTraps;
	}

	@Override
	public String toString()
	{
		return myTarget.toString() + " " + myTraps.toString();
	}
}
