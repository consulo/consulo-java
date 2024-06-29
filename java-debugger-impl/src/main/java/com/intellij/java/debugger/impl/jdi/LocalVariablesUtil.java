/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.jdi;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.DebugProcess;
import com.intellij.java.debugger.engine.StackFrameContext;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.DebuggerUtilsEx;
import com.intellij.java.debugger.impl.SimpleStackFrameContext;
import com.intellij.java.debugger.impl.engine.ContextUtil;
import com.intellij.java.language.psi.*;
import consulo.application.ReadAction;
import consulo.internal.com.sun.jdi.InternalException;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.StackFrame;
import consulo.internal.com.sun.jdi.Value;
import consulo.internal.com.sun.tools.jdi.*;
import consulo.internal.org.objectweb.asm.MethodVisitor;
import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * From JDI sources:
 * <p>
 * validateStackFrame();
 * validateMirrors(variables);
 * <p>
 * int count = variables.size();
 * JDWP.StackFrame.GetValues.SlotInfo[] slots =
 * new JDWP.StackFrame.GetValues.SlotInfo[count];
 * <p>
 * for (int i=0; i<count; ++i) {
 * LocalVariableImpl variable = (LocalVariableImpl)variables.get(i);
 * if (!variable.isVisible(this)) {
 * throw new IllegalArgumentException(variable.name() +
 * " is not valid at this frame location");
 * }
 * slots[i] = new JDWP.StackFrame.GetValues.SlotInfo(variable.slot(),
 * (byte)variable.signature().charAt(0));
 * }
 * <p>
 * PacketStream ps;
 * <p>
 * synchronized (vm.state()) {
 * validateStackFrame();
 * ps = JDWP.StackFrame.GetValues.enqueueCommand(vm, thread, id, slots);
 * }
 * <p>
 * ValueImpl[] values;
 * try {
 * values = JDWP.StackFrame.GetValues.waitForReply(vm, ps).values;
 * } catch (JDWPException exc) {
 * switch (exc.errorCode()) {
 * case JDWP.Error.INVALID_FRAMEID:
 * case JDWP.Error.THREAD_NOT_SUSPENDED:
 * case JDWP.Error.INVALID_THREAD:
 * throw new InvalidStackFrameException();
 * default:
 * throw exc.toJDIException();
 * }
 * }
 * <p>
 * if (count != values.length) {
 * throw new InternalException(
 * "Wrong number of values returned from target VM");
 * }
 * Map map = new HashMap(count);
 * for (int i=0; i<count; ++i) {
 * LocalVariableImpl variable = (LocalVariableImpl)variables.get(i);
 * map.put(variable, values[i]);
 * }
 * return map;
 */
public class LocalVariablesUtil
{
	private static final Logger LOG = Logger.getInstance(LocalVariablesUtil.class);

	public static Map<DecompiledLocalVariable, Value> fetchValues(@Nonnull StackFrameProxyImpl frameProxy, DebugProcess process, boolean full) throws Exception
	{
		Map<DecompiledLocalVariable, Value> map = new LinkedHashMap<>(); // LinkedHashMap for correct order

		Location location = frameProxy.location();
		consulo.internal.com.sun.jdi.Method method = location.method();
		final int firstLocalVariableSlot = getFirstLocalsSlot(method);

		// gather code variables names
		MultiMap<Integer, String> namesMap = full ? calcNames(new SimpleStackFrameContext(frameProxy, process), firstLocalVariableSlot) : MultiMap.empty();

		// first add arguments
		int slot = getFirstArgsSlot(method);
		List<String> typeNames = method.argumentTypeNames();
		List<Value> argValues = frameProxy.getArgumentValues();
		for(int i = 0; i < argValues.size(); i++)
		{
			map.put(new DecompiledLocalVariable(slot, true, null, namesMap.get(slot)), argValues.get(i));
			slot += getTypeSlotSize(typeNames.get(i));
		}

		if(!full)
		{
			return map;
		}

		// now try to fetch stack values
		List<DecompiledLocalVariable> vars = collectVariablesFromBytecode(frameProxy.getVirtualMachine(), location, namesMap);
		StackFrame frame = frameProxy.getStackFrame();
		int size = vars.size();
		while(size > 0)
		{
			try
			{
				return fetchSlotValues(map, vars.subList(0, size), frame);
			}
			catch(Exception e)
			{
				LOG.debug(e);
			}
			size--; // try with the reduced list
		}

		return map;
	}

	private static Map<DecompiledLocalVariable, Value> fetchSlotValues(Map<DecompiledLocalVariable, Value> map, List<DecompiledLocalVariable> vars, StackFrame frame) throws Exception
	{
		final Long frameId = ((StackFrameImpl) frame).id();
		final VirtualMachineImpl vm = (VirtualMachineImpl) frame.virtualMachine();

		JDWP.StackFrame.GetValues.SlotInfo[] slotInfoArray = createSlotInfoArray(vars);

		PacketStream ps;
		final VMState vmState = vm.state();
		synchronized(vmState)
		{
			ps = JDWP.StackFrame.GetValues.enqueueCommand(vm, (ThreadReferenceImpl) frame.thread(), frameId, slotInfoArray);
		}

		JDWP.StackFrame.GetValues reply = JDWP.StackFrame.GetValues.waitForReply(vm, ps);
		final Value[] values = reply.values;
		if(vars.size() != values.length)
		{
			throw new InternalException("Wrong number of values returned from target VM");
		}
		int idx = 0;
		for(DecompiledLocalVariable var : vars)
		{
			map.put(var, values[idx++]);
		}
		return map;
	}

	public static void setValue(StackFrame frame, int slot, Value value) throws EvaluateException
	{
		try
		{
			StackFrameImpl stackFrameImpl = (StackFrameImpl) frame;
			final long frameId = stackFrameImpl.id();
			final VirtualMachineImpl vm = (VirtualMachineImpl) frame.virtualMachine();

			JDWP.StackFrame.SetValues.SlotInfo[] slotInfoArray = createSlotInfoArraySet(slot, value);

			PacketStream ps;
			final VMState vmState = vm.state();
			synchronized(vmState)
			{
				ps = JDWP.StackFrame.SetValues.enqueueCommand(vm, (ThreadReferenceImpl) frame.thread(), frameId, slotInfoArray);
			}

			JDWP.StackFrame.SetValues.waitForReply(vm, ps);
		}
		catch(Exception e)
		{
			throw new EvaluateException("Unable to set value", e);
		}
	}

	private static JDWP.StackFrame.SetValues.SlotInfo[] createSlotInfoArraySet(int slot, Value value)
	{
		return new JDWP.StackFrame.SetValues.SlotInfo[]{new JDWP.StackFrame.SetValues.SlotInfo(slot, (ValueImpl) value)};
	}

	private static JDWP.StackFrame.GetValues.SlotInfo[] createSlotInfoArray(List<DecompiledLocalVariable> vars)
	{
		final JDWP.StackFrame.GetValues.SlotInfo[] slots = new JDWP.StackFrame.GetValues.SlotInfo[vars.size()];

		for(int i = 0; i < vars.size(); i++)
		{
			DecompiledLocalVariable var = vars.get(i);

			slots[i] = new JDWP.StackFrame.GetValues.SlotInfo(var.getSlot(), (byte) var.getSignature().charAt(0));
		}

		return slots;
	}

	@Nonnull
	private static List<DecompiledLocalVariable> collectVariablesFromBytecode(VirtualMachineProxyImpl vm, Location location, MultiMap<Integer, String> namesMap)
	{
		if(!vm.canGetBytecodes())
		{
			return Collections.emptyList();
		}
		try
		{
			LOG.assertTrue(location != null);
			final consulo.internal.com.sun.jdi.Method method = location.method();
			final Location methodLocation = method.location();
			if(methodLocation == null || methodLocation.codeIndex() < 0)
			{
				// native or abstract method
				return Collections.emptyList();
			}

			long codeIndex = location.codeIndex();
			if(codeIndex > 0)
			{
				final byte[] bytecodes = method.bytecodes();
				if(bytecodes != null && bytecodes.length > 0)
				{
					final int firstLocalVariableSlot = getFirstLocalsSlot(method);
					final HashMap<Integer, DecompiledLocalVariable> usedVars = new HashMap<>();
					MethodBytecodeUtil.visit(method, codeIndex, new MethodVisitor(Opcodes.API_VERSION)
					{
						@Override
						public void visitVarInsn(int opcode, int slot)
						{
							if(slot >= firstLocalVariableSlot)
							{
								DecompiledLocalVariable variable = usedVars.get(slot);
								String typeSignature = MethodBytecodeUtil.getVarInstructionType(opcode).getDescriptor();
								if(variable == null || !typeSignature.equals(variable.getSignature()))
								{
									variable = new DecompiledLocalVariable(slot, false, typeSignature, namesMap.get(slot));
									usedVars.put(slot, variable);
								}
							}
						}
					}, false);
					if(usedVars.isEmpty())
					{
						return Collections.emptyList();
					}

					List<DecompiledLocalVariable> vars = new ArrayList<>(usedVars.values());
					vars.sort(Comparator.comparingInt(DecompiledLocalVariable::getSlot));
					return vars;
				}
			}
		}
		catch(UnsupportedOperationException ignored)
		{
		}
		catch(Exception e)
		{
			LOG.error(e);
		}
		return Collections.emptyList();
	}

	@Nonnull
	private static MultiMap<Integer, String> calcNames(@Nonnull final StackFrameContext context, final int firstLocalsSlot)
	{
		SourcePosition position = ContextUtil.getSourcePosition(context);
		if(position != null)
		{
			return ReadAction.compute(() ->
			{
				PsiElement element = position.getElementAt();
				PsiElement method = DebuggerUtilsEx.getContainingMethod(element);
				if(method != null)
				{
					MultiMap<Integer, String> res = new MultiMap<>();
					int slot = Math.max(0, firstLocalsSlot - getParametersStackSize(method));
					for(PsiParameter parameter : DebuggerUtilsEx.getParameters(method))
					{
						res.putValue(slot, parameter.getName());
						slot += getTypeSlotSize(parameter.getType());
					}
					PsiElement body = DebuggerUtilsEx.getBody(method);
					if(body != null)
					{
						try
						{
							body.accept(new LocalVariableNameFinder(firstLocalsSlot, res, element));
						}
						catch(Exception e)
						{
							LOG.info(e);
						}
					}
					return res;
				}
				return MultiMap.empty();
			});
		}
		return MultiMap.empty();
	}

	/**
	 * Walker that preserves the order of locals declarations but walks only visible scope
	 */
	private static class LocalVariableNameFinder extends JavaRecursiveElementVisitor
	{
		private final MultiMap<Integer, String> myNames;
		private int myCurrentSlotIndex;
		private final PsiElement myElement;
		private final Deque<Integer> myIndexStack = new LinkedList<>();
		private boolean myReached = false;

		public LocalVariableNameFinder(int startSlot, MultiMap<Integer, String> names, PsiElement element)
		{
			myNames = names;
			myCurrentSlotIndex = startSlot;
			myElement = element;
		}

		private boolean shouldVisit(PsiElement scope)
		{
			return !myReached && PsiTreeUtil.isContextAncestor(scope, myElement, false);
		}

		@Override
		public void visitElement(PsiElement element)
		{
			if(element == myElement)
			{
				myReached = true;
			}
			else
			{
				super.visitElement(element);
			}
		}

		@Override
		public void visitLocalVariable(PsiLocalVariable variable)
		{
			super.visitLocalVariable(variable);
			if(!myReached)
			{
				appendName(variable.getName());
				myCurrentSlotIndex += getTypeSlotSize(variable.getType());
			}
		}

		public void visitSynchronizedStatement(PsiSynchronizedStatement statement)
		{
			if(shouldVisit(statement))
			{
				myIndexStack.push(myCurrentSlotIndex);
				try
				{
					appendName("<monitor>");
					myCurrentSlotIndex++;
					super.visitSynchronizedStatement(statement);
				}
				finally
				{
					myCurrentSlotIndex = myIndexStack.pop();
				}
			}
		}

		private void appendName(String varName)
		{
			myNames.putValue(myCurrentSlotIndex, varName);
		}

		@Override
		public void visitCodeBlock(PsiCodeBlock block)
		{
			if(shouldVisit(block))
			{
				myIndexStack.push(myCurrentSlotIndex);
				try
				{
					super.visitCodeBlock(block);
				}
				finally
				{
					myCurrentSlotIndex = myIndexStack.pop();
				}
			}
		}

		@Override
		public void visitForStatement(PsiForStatement statement)
		{
			if(shouldVisit(statement))
			{
				myIndexStack.push(myCurrentSlotIndex);
				try
				{
					super.visitForStatement(statement);
				}
				finally
				{
					myCurrentSlotIndex = myIndexStack.pop();
				}
			}
		}

		@Override
		public void visitForeachStatement(PsiForeachStatement statement)
		{
			if(shouldVisit(statement))
			{
				myIndexStack.push(myCurrentSlotIndex);
				try
				{
					super.visitForeachStatement(statement);
				}
				finally
				{
					myCurrentSlotIndex = myIndexStack.pop();
				}
			}
		}

		@Override
		public void visitCatchSection(PsiCatchSection section)
		{
			if(shouldVisit(section))
			{
				myIndexStack.push(myCurrentSlotIndex);
				try
				{
					super.visitCatchSection(section);
				}
				finally
				{
					myCurrentSlotIndex = myIndexStack.pop();
				}
			}
		}

		@Override
		public void visitResourceList(PsiResourceList resourceList)
		{
			if(shouldVisit(resourceList))
			{
				myIndexStack.push(myCurrentSlotIndex);
				try
				{
					super.visitResourceList(resourceList);
				}
				finally
				{
					myCurrentSlotIndex = myIndexStack.pop();
				}
			}
		}

		@Override
		public void visitClass(PsiClass aClass)
		{
			// skip local and anonymous classes
		}
	}

	private static int getParametersStackSize(PsiElement method)
	{
		return Arrays.stream(DebuggerUtilsEx.getParameters(method)).mapToInt(parameter -> getTypeSlotSize(parameter.getType())).sum();
	}

	private static int getTypeSlotSize(PsiType varType)
	{
		if(PsiType.DOUBLE.equals(varType) || PsiType.LONG.equals(varType))
		{
			return 2;
		}
		return 1;
	}

	private static int getFirstArgsSlot(consulo.internal.com.sun.jdi.Method method)
	{
		return method.isStatic() ? 0 : 1;
	}

	private static int getFirstLocalsSlot(consulo.internal.com.sun.jdi.Method method)
	{
		return getFirstArgsSlot(method) + method.argumentTypeNames().stream().mapToInt(LocalVariablesUtil::getTypeSlotSize).sum();
	}

	private static int getTypeSlotSize(String name)
	{
		if(PsiKeyword.DOUBLE.equals(name) || PsiKeyword.LONG.equals(name))
		{
			return 2;
		}
		return 1;
	}
}
