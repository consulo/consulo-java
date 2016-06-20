/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInspection.bytecodeAnalysis;

import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.InstanceOfCheckValue;
import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.NullValue;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.Identity;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.NPE;

import java.util.List;

import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.AbstractInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodInsnNode;
import org.jetbrains.org.objectweb.asm.tree.TypeInsnNode;
import org.jetbrains.org.objectweb.asm.tree.analysis.AnalyzerException;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;

abstract class NullityInterpreter extends BasicInterpreter
{
	boolean top;
	final boolean nullableAnalysis;
	final int nullityMask;
	private PResults.PResult subResult = Identity;
	protected boolean taken;

	NullityInterpreter(boolean nullableAnalysis, int nullityMask)
	{
		this.nullableAnalysis = nullableAnalysis;
		this.nullityMask = nullityMask;
	}

	abstract PResults.PResult combine(PResults.PResult res1, PResults.PResult res2) throws AnalyzerException;

	public PResults.PResult getSubResult()
	{
		return subResult;
	}

	void reset(boolean taken)
	{
		subResult = Identity;
		top = false;
		this.taken = taken;
	}

	@Override
	public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException
	{
		switch(insn.getOpcode())
		{
			case GETFIELD:
			case ARRAYLENGTH:
			case MONITORENTER:
				if(value instanceof AbstractValues.ParamValue)
				{
					subResult = NPE;
				}
				break;
			case CHECKCAST:
				if(value instanceof AbstractValues.ParamValue)
				{
					return new AbstractValues.ParamValue(Type.getObjectType(((TypeInsnNode) insn).desc));
				}
				break;
			case INSTANCEOF:
				if(value instanceof AbstractValues.ParamValue)
				{
					return InstanceOfCheckValue;
				}
				break;
			default:

		}
		return super.unaryOperation(insn, value);
	}

	@Override
	public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) throws AnalyzerException
	{
		switch(insn.getOpcode())
		{
			case IALOAD:
			case LALOAD:
			case FALOAD:
			case DALOAD:
			case AALOAD:
			case BALOAD:
			case CALOAD:
			case SALOAD:
				if(value1 instanceof AbstractValues.ParamValue)
				{
					subResult = NPE;
				}
				break;
			case PUTFIELD:
				if(value1 instanceof AbstractValues.ParamValue)
				{
					subResult = NPE;
				}
				if(nullableAnalysis && value2 instanceof AbstractValues.ParamValue)
				{
					subResult = NPE;
				}
				break;
			default:
		}
		return super.binaryOperation(insn, value1, value2);
	}

	@Override
	public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3) throws AnalyzerException
	{
		switch(insn.getOpcode())
		{
			case IASTORE:
			case LASTORE:
			case FASTORE:
			case DASTORE:
			case BASTORE:
			case CASTORE:
			case SASTORE:
				if(value1 instanceof AbstractValues.ParamValue)
				{
					subResult = NPE;
				}
				break;
			case AASTORE:
				if(value1 instanceof AbstractValues.ParamValue)
				{
					subResult = NPE;
				}
				if(nullableAnalysis && value3 instanceof AbstractValues.ParamValue)
				{
					subResult = NPE;
				}
				break;
			default:
		}
		return null;
	}

	@Override
	public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException
	{
		int opcode = insn.getOpcode();
		boolean isStaticInvoke = opcode == INVOKESTATIC;
		int shift = isStaticInvoke ? 0 : 1;
		if((opcode == INVOKESPECIAL || opcode == INVOKEINTERFACE || opcode == INVOKEVIRTUAL) && values.get(0) instanceof AbstractValues.ParamValue)
		{
			subResult = NPE;
		}
		switch(opcode)
		{
			case INVOKEINTERFACE:
				if(nullableAnalysis)
				{
					for(int i = shift; i < values.size(); i++)
					{
						if(values.get(i) instanceof AbstractValues.ParamValue)
						{
							top = true;
							return super.naryOperation(insn, values);
						}
					}
				}
				break;
			case INVOKESTATIC:
			case INVOKESPECIAL:
			case INVOKEVIRTUAL:
				boolean stable = opcode == INVOKESTATIC || opcode == INVOKESPECIAL;
				MethodInsnNode methodNode = (MethodInsnNode) insn;
				Method method = new Method(methodNode.owner, methodNode.name, methodNode.desc);
				for(int i = shift; i < values.size(); i++)
				{
					BasicValue value = values.get(i);
					if(value instanceof AbstractValues.ParamValue || (NullValue == value && nullityMask == Direction.In.NULLABLE_MASK && "<init>".equals(methodNode.name)))
					{
						subResult = combine(subResult, new PResults.ConditionalNPE(new Key(method, new Direction.In(i - shift, nullityMask), stable)));
					}
				}
				break;
			default:
		}
		return super.naryOperation(insn, values);
	}
}
