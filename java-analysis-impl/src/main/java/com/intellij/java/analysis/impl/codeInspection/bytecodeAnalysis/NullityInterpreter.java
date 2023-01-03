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

package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.InvokeDynamicInsnNode;
import consulo.internal.org.objectweb.asm.tree.MethodInsnNode;
import consulo.internal.org.objectweb.asm.tree.TypeInsnNode;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicInterpreter;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;

import java.util.List;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.InstanceOfCheckValue;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.NullValue;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.PResults.Identity;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.PResults.NPE;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.PResults.*;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.*;

abstract class NullityInterpreter extends BasicInterpreter
{
	boolean top;
	final boolean nullableAnalysis;
	final boolean nullable;
	private PResult subResult = Identity;
	protected boolean taken;

	NullityInterpreter(boolean nullableAnalysis, boolean nullable)
	{
		super(Opcodes.API_VERSION);
		this.nullableAnalysis = nullableAnalysis;
		this.nullable = nullable;
	}

	abstract PResult combine(PResult res1, PResult res2) throws AnalyzerException;

	public PResult getSubResult()
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
				if(value instanceof ParamValue)
				{
					subResult = NPE;
				}
				break;
			case CHECKCAST:
				if(value instanceof ParamValue)
				{
					return new ParamValue(Type.getObjectType(((TypeInsnNode) insn).desc));
				}
				break;
			case INSTANCEOF:
				if(value instanceof ParamValue)
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
				if(value1 instanceof ParamValue)
				{
					subResult = NPE;
				}
				break;
			case PUTFIELD:
				if(value1 instanceof ParamValue)
				{
					subResult = NPE;
				}
				if(nullableAnalysis && value2 instanceof ParamValue)
				{
					subResult = NPE;
				}
				break;
			default:
		}
		return super.binaryOperation(insn, value1, value2);
	}

	@Override
	public BasicValue ternaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2, BasicValue value3)
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
				if(value1 instanceof ParamValue)
				{
					subResult = NPE;
				}
				break;
			case AASTORE:
				if(value1 instanceof ParamValue)
				{
					subResult = NPE;
				}
				if(nullableAnalysis && value3 instanceof ParamValue)
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
		switch(opcode)
		{
			case INVOKEINTERFACE:
			case INVOKESPECIAL:
			case INVOKESTATIC:
			case INVOKEVIRTUAL:
				MethodInsnNode methodNode = (MethodInsnNode) insn;
				methodCall(opcode, new Member(methodNode.owner, methodNode.name, methodNode.desc), values);
				break;
			case INVOKEDYNAMIC:
				LambdaIndy lambda = LambdaIndy.from((InvokeDynamicInsnNode) insn);
				if(lambda != null)
				{
					int targetOpcode = lambda.getAssociatedOpcode();
					if(targetOpcode != -1)
					{
						methodCall(targetOpcode, lambda.getMethod(), lambda.getLambdaMethodArguments(values, this::newValue));
					}
				}
			default:
		}
		return super.naryOperation(insn, values);
	}

	private void methodCall(int opcode, Member method, List<? extends BasicValue> values) throws AnalyzerException
	{
		if(opcode != INVOKESTATIC && values.remove(0) instanceof ParamValue)
		{
			subResult = NPE;
		}

		if(opcode == INVOKEINTERFACE)
		{
			if(nullableAnalysis)
			{
				for(BasicValue value : values)
				{
					if(value instanceof ParamValue)
					{
						top = true;
						break;
					}
				}
			}
		}
		else
		{
			boolean stable = opcode == INVOKESTATIC || opcode == INVOKESPECIAL;
			for(int i = 0; i < values.size(); i++)
			{
				BasicValue value = values.get(i);
				if(value instanceof ParamValue || (NullValue == value && nullable && "<init>".equals(method.methodName)))
				{
					subResult = combine(subResult, new ConditionalNPE(new EKey(method, new Direction.In(i, nullable), stable)));
				}
			}
		}
	}
}

