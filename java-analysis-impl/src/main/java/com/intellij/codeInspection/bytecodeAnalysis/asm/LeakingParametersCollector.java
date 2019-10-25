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

package com.intellij.codeInspection.bytecodeAnalysis.asm;

import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.MethodNode;

import java.util.List;

import static consulo.internal.org.objectweb.asm.Opcodes.*;

class LeakingParametersCollector extends ParametersUsage
{
	final boolean[] leaking;
	final boolean[] nullableLeaking;

	LeakingParametersCollector(MethodNode methodNode)
	{
		super(methodNode);
		leaking = new boolean[arity];
		nullableLeaking = new boolean[arity];
	}

	@Override
	public ParamsValue unaryOperation(AbstractInsnNode insn, ParamsValue value)
	{
		switch(insn.getOpcode())
		{
			case GETFIELD:
			case ARRAYLENGTH:
			case MONITORENTER:
			case INSTANCEOF:
			case IRETURN:
			case ARETURN:
			case IFNONNULL:
			case IFNULL:
			case IFEQ:
			case IFNE:
				boolean[] params = value.params;
				for(int i = 0; i < arity; i++)
				{
					leaking[i] |= params[i];
				}
				break;
			default:
		}
		return super.unaryOperation(insn, value);
	}

	@Override
	public ParamsValue binaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2)
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
			{
				boolean[] params = value1.params;
				for(int i = 0; i < arity; i++)
				{
					leaking[i] |= params[i];
				}
				break;
			}
			case PUTFIELD:
			{
				boolean[] params = value1.params;
				for(int i = 0; i < arity; i++)
				{
					leaking[i] |= params[i];
				}
				params = value2.params;
				for(int i = 0; i < arity; i++)
				{
					nullableLeaking[i] |= params[i];
				}
				break;
			}
			default:
		}
		return super.binaryOperation(insn, value1, value2);
	}

	@Override
	public ParamsValue ternaryOperation(AbstractInsnNode insn, ParamsValue value1, ParamsValue value2, ParamsValue value3)
	{
		boolean[] params;
		switch(insn.getOpcode())
		{
			case IASTORE:
			case LASTORE:
			case FASTORE:
			case DASTORE:
			case BASTORE:
			case CASTORE:
			case SASTORE:
				params = value1.params;
				for(int i = 0; i < arity; i++)
				{
					leaking[i] |= params[i];
				}
				break;
			case AASTORE:
				params = value1.params;
				for(int i = 0; i < arity; i++)
				{
					leaking[i] |= params[i];
				}
				params = value3.params;
				for(int i = 0; i < arity; i++)
				{
					nullableLeaking[i] |= params[i];
				}
				break;
			default:
		}
		return null;
	}

	@Override
	public ParamsValue naryOperation(AbstractInsnNode insn, List<? extends ParamsValue> values)
	{
		switch(insn.getOpcode())
		{
			case INVOKESTATIC:
			case INVOKESPECIAL:
			case INVOKEVIRTUAL:
			case Opcodes.INVOKEINTERFACE:
				for(ParamsValue value : values)
				{
					boolean[] params = value.params;
					for(int i = 0; i < arity; i++)
					{
						leaking[i] |= params[i];
					}
				}
				break;
			default:
		}
		return super.naryOperation(insn, values);
	}
}