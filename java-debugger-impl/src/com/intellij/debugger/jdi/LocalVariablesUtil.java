/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.debugger.jdi;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import consulo.internal.com.sun.jdi.InternalException;
import consulo.internal.com.sun.jdi.StackFrame;
import consulo.internal.com.sun.jdi.Value;
import consulo.internal.com.sun.jdi.VirtualMachine;
import consulo.internal.com.sun.tools.jdi.JDWP;
import consulo.internal.com.sun.tools.jdi.StackFrameImpl;
import consulo.internal.com.sun.tools.jdi.ThreadReferenceImpl;
import consulo.internal.com.sun.tools.jdi.VirtualMachineImpl;

public class LocalVariablesUtil
{
	public static Map<DecompiledLocalVariable, Value> fetchValues(StackFrame frame, Collection<DecompiledLocalVariable> vars) throws Exception
	{
		final long frameId = ((StackFrameImpl)frame).id();

		final VirtualMachine vm = frame.virtualMachine();

		JDWP.StackFrame.GetValues.SlotInfo[] slotInfoArray = createSlotInfoArray(vars);

		JDWP.StackFrame.GetValues process = JDWP.StackFrame.GetValues.process((VirtualMachineImpl) vm, (ThreadReferenceImpl)frame.thread(), frameId,
				slotInfoArray);

		final Value[] values = process.values;
		if(vars.size() != values.length)
		{
			throw new InternalException("Wrong number of values returned from target VM");
		}
		final Map<DecompiledLocalVariable, Value> map = new HashMap<DecompiledLocalVariable, Value>(vars.size());
		int idx = 0;
		for(DecompiledLocalVariable var : vars)
		{
			map.put(var, values[idx++]);
		}
		return map;
	}

	private static JDWP.StackFrame.GetValues.SlotInfo[] createSlotInfoArray(Collection<DecompiledLocalVariable> vars) throws Exception
	{
		final JDWP.StackFrame.GetValues.SlotInfo[]  arrayInstance = new JDWP.StackFrame.GetValues.SlotInfo[vars.size()];

		int idx = 0;
		for(DecompiledLocalVariable var : vars)
		{
			arrayInstance[idx] = new JDWP.StackFrame.GetValues.SlotInfo(var.getSlot(), (byte) var.getSignature().charAt(0));
		}

		return arrayInstance;
	}
}
