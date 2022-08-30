/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.Direction.ParamValueBasedDirection;
import consulo.internal.org.objectweb.asm.Handle;
import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.*;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicInterpreter;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;

import java.util.HashSet;
import java.util.List;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.*;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.Direction.Out;

class InOutInterpreter extends BasicInterpreter
{
	final Direction.ParamValueBasedDirection direction;
	final InsnList insns;
	final boolean[] resultOrigins;
	final boolean nullAnalysis;

	boolean deReferenced;

	InOutInterpreter(Direction direction, InsnList insns, boolean[] resultOrigins)
	{
		super(Opcodes.API_VERSION);
		this.insns = insns;
		this.resultOrigins = resultOrigins;
		if(direction instanceof ParamValueBasedDirection)
		{
			this.direction = (ParamValueBasedDirection) direction;
			this.nullAnalysis = this.direction.inValue == Value.Null;
		}
		else
		{
			this.direction = null;
			this.nullAnalysis = false;
		}
	}

	@Override
	public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException
	{
		boolean propagate = resultOrigins[insns.indexOf(insn)];
		if(propagate)
		{
			switch(insn.getOpcode())
			{
				case ICONST_0:
					return FalseValue;
				case ICONST_1:
					return TrueValue;
				case ACONST_NULL:
					return NullValue;
				case LDC:
					Object cst = ((LdcInsnNode) insn).cst;
					if(cst instanceof Type)
					{
						Type type = (Type) cst;
						if(type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)
						{
							return CLASS_VALUE;
						}
						if(type.getSort() == Type.METHOD)
						{
							return METHOD_VALUE;
						}
					}
					else if(cst instanceof String)
					{
						return STRING_VALUE;
					}
					else if(cst instanceof Handle)
					{
						return METHOD_HANDLE_VALUE;
					}
					break;
				case NEW:
					return new NotNullValue(Type.getObjectType(((TypeInsnNode) insn).desc));
				default:
			}
		}
		return super.newOperation(insn);
	}

	@Override
	public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException
	{
		boolean propagate = resultOrigins[insns.indexOf(insn)];
		switch(insn.getOpcode())
		{
			case GETFIELD:
			case ARRAYLENGTH:
			case MONITORENTER:
				if(nullAnalysis && value instanceof ParamValue)
				{
					deReferenced = true;
				}
				return super.unaryOperation(insn, value);
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
			case NEWARRAY:
			case ANEWARRAY:
				if(propagate)
				{
					return new NotNullValue(super.unaryOperation(insn, value).getType());
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
			case PUTFIELD:
				if(nullAnalysis && value1 instanceof ParamValue)
				{
					deReferenced = true;
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
			case AASTORE:
			case BASTORE:
			case CASTORE:
			case SASTORE:
				if(nullAnalysis && value1 instanceof ParamValue)
				{
					deReferenced = true;
				}
			default:
		}
		return null;
	}

	@Override
	public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException
	{
		boolean propagate = resultOrigins[insns.indexOf(insn)];
		int opCode = insn.getOpcode();
		int shift = opCode == INVOKESTATIC ? 0 : 1;

		switch(opCode)
		{
			case INVOKESPECIAL:
			case INVOKEINTERFACE:
			case INVOKEVIRTUAL:
				if(nullAnalysis && values.get(0) instanceof ParamValue)
				{
					deReferenced = true;
					return super.naryOperation(insn, values);
				}
		}

		if(propagate)
		{
			switch(opCode)
			{
				case INVOKESTATIC:
				case INVOKESPECIAL:
				case INVOKEVIRTUAL:
				case INVOKEINTERFACE:
					boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
					MethodInsnNode mNode = (MethodInsnNode) insn;
					Member method = new Member(mNode.owner, mNode.name, mNode.desc);
					Type retType = Type.getReturnType(mNode.desc);
					boolean isRefRetType = retType.getSort() == Type.OBJECT || retType.getSort() == Type.ARRAY;
					if(!Type.VOID_TYPE.equals(retType))
					{
						if(direction != null)
						{
							HashSet<EKey> keys = new HashSet<>();
							for(int i = shift; i < values.size(); i++)
							{
								if(values.get(i) instanceof ParamValue)
								{
									keys.add(new EKey(method, direction.withIndex(i - shift), stable));
								}
							}
							if(isRefRetType)
							{
								keys.add(new EKey(method, Out, stable));
							}
							if(!keys.isEmpty())
							{
								return new CallResultValue(retType, keys);
							}
						}
						else if(isRefRetType)
						{
							HashSet<EKey> keys = new HashSet<>();
							keys.add(new EKey(method, Out, stable));
							return new CallResultValue(retType, keys);
						}
					}
					break;
				case INVOKEDYNAMIC:
					InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
					if(LambdaIndy.from(indy) != null || ClassDataIndexer.STRING_CONCAT_FACTORY.equals(indy.bsm.getOwner()))
					{
						// indy producing lambda or string concatenation is never null
						return new NotNullValue(Type.getReturnType(indy.desc));
					}
					break;
				case MULTIANEWARRAY:
					return new NotNullValue(super.naryOperation(insn, values).getType());
				default:
			}
		}
		return super.naryOperation(insn, values);
	}
}
