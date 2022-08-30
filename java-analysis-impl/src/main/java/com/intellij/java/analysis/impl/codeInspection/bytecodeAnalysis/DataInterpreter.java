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

import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.util.ArrayUtil;
import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.*;
import consulo.internal.org.objectweb.asm.tree.analysis.Interpreter;

import javax.annotation.Nullable;
import java.util.List;

class DataInterpreter extends Interpreter<DataValue>
{
	private final MethodNode methodNode;
	private int param = -1;
	final EffectQuantum[] effects;
	DataValue returnValue = null;

	protected DataInterpreter(MethodNode methodNode)
	{
		super(Opcodes.API_VERSION);
		this.methodNode = methodNode;
		this.effects = new EffectQuantum[methodNode.instructions.size()];
	}

	@Override
	public DataValue newParameterValue(boolean isInstanceMethod, int local, Type type)
	{
		param++;
		if(ASMUtils.isThisType(type))
			return DataValue.ThisDataValue;
		int n = isInstanceMethod ? param - 1 : param;
		if(n >= 0 && ASMUtils.isReferenceType(type))
		{
			return DataValue.ParameterDataValue.create(n);
		}
		return newValue(type);
	}

	@Override
	public DataValue newValue(Type type)
	{
		if(type == null)
			return DataValue.UnknownDataValue1;
		if(type == Type.VOID_TYPE)
			return null;
		if(ASMUtils.isThisType(type))
			return DataValue.ThisDataValue;
		return type.getSize() == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
	}

	@Override
	public DataValue newOperation(AbstractInsnNode insn)
	{
		switch(insn.getOpcode())
		{
			case Opcodes.NEW:
				return DataValue.LocalDataValue;
			case Opcodes.LCONST_0:
			case Opcodes.LCONST_1:
			case Opcodes.DCONST_0:
			case Opcodes.DCONST_1:
				return DataValue.UnknownDataValue2;
			case Opcodes.LDC:
				Object cst = ((LdcInsnNode) insn).cst;
				int size = (cst instanceof Long || cst instanceof Double) ? 2 : 1;
				return size == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
			case Opcodes.GETSTATIC:
				FieldInsnNode fieldInsn = (FieldInsnNode) insn;
				Member method = new Member(fieldInsn.owner, fieldInsn.name, fieldInsn.desc);
				EKey key = new EKey(method, Direction.Volatile, true);
				effects[methodNode.instructions.indexOf(insn)] = new EffectQuantum.FieldReadQuantum(key);
				size = Type.getType(((FieldInsnNode) insn).desc).getSize();
				return size == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
			default:
				return DataValue.UnknownDataValue1;
		}
	}

	@Override
	public DataValue binaryOperation(AbstractInsnNode insn, DataValue value1, DataValue value2)
	{
		switch(insn.getOpcode())
		{
			case Opcodes.LALOAD:
			case Opcodes.DALOAD:
			case Opcodes.LADD:
			case Opcodes.DADD:
			case Opcodes.LSUB:
			case Opcodes.DSUB:
			case Opcodes.LMUL:
			case Opcodes.DMUL:
			case Opcodes.LDIV:
			case Opcodes.DDIV:
			case Opcodes.LREM:
			case Opcodes.LSHL:
			case Opcodes.LSHR:
			case Opcodes.LUSHR:
			case Opcodes.LAND:
			case Opcodes.LOR:
			case Opcodes.LXOR:
				return DataValue.UnknownDataValue2;
			case Opcodes.PUTFIELD:
				final EffectQuantum effectQuantum = getChangeQuantum(value1);
				int insnIndex = methodNode.instructions.indexOf(insn);
				effects[insnIndex] = effectQuantum;
				return DataValue.UnknownDataValue1;
			default:
				return DataValue.UnknownDataValue1;
		}
	}

	@Nullable
	private static EffectQuantum getChangeQuantum(DataValue value)
	{
		if(value == DataValue.ThisDataValue || value == DataValue.OwnedDataValue)
		{
			return EffectQuantum.ThisChangeQuantum;
		}
		if(value == DataValue.LocalDataValue)
		{
			return null;
		}
		if(value instanceof DataValue.ParameterDataValue)
		{
			return new EffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue) value).n);
		}
		if(value instanceof DataValue.ReturnDataValue)
		{
			return new EffectQuantum.ReturnChangeQuantum(((DataValue.ReturnDataValue) value).key);
		}
		return EffectQuantum.TopEffectQuantum;
	}

	@Override
	public DataValue copyOperation(AbstractInsnNode insn, DataValue value)
	{
		return value;
	}

	@Override
	public DataValue naryOperation(AbstractInsnNode insn, List<? extends DataValue> values)
	{
		int insnIndex = methodNode.instructions.indexOf(insn);
		int opCode = insn.getOpcode();
		switch(opCode)
		{
			case Opcodes.MULTIANEWARRAY:
				return DataValue.LocalDataValue;
			case Opcodes.INVOKEDYNAMIC:
				// Lambda creation (w/o invocation) and StringConcatFactory have no side-effect
				InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
				if(LambdaIndy.from(indy) == null && !ClassDataIndexer.STRING_CONCAT_FACTORY.equals(indy.bsm.getOwner()))
				{
					effects[insnIndex] = EffectQuantum.TopEffectQuantum;
				}
				return (ASMUtils.getReturnSizeFast((indy).desc) == 1) ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
			case Opcodes.INVOKEVIRTUAL:
			case Opcodes.INVOKESPECIAL:
			case Opcodes.INVOKESTATIC:
			case Opcodes.INVOKEINTERFACE:
				boolean stable = opCode == Opcodes.INVOKESPECIAL || opCode == Opcodes.INVOKESTATIC;
				MethodInsnNode mNode = ((MethodInsnNode) insn);
				DataValue[] data = values.toArray(DataValue.EMPTY);
				Member method = new Member(mNode.owner, mNode.name, mNode.desc);
				EKey key = new EKey(method, Direction.Pure, stable);
				EffectQuantum quantum = new EffectQuantum.CallQuantum(key, data, opCode == Opcodes.INVOKESTATIC);
				DataValue result;
				if(ASMUtils.getReturnSizeFast(mNode.desc) == 1)
				{
					if(ASMUtils.isReferenceReturnType(mNode.desc))
					{
						result = new DataValue.ReturnDataValue(key);
					}
					else
					{
						result = DataValue.UnknownDataValue1;
					}
				}
				else
				{
					result = DataValue.UnknownDataValue2;
				}
				if(HardCodedPurity.getInstance().isPureMethod(method))
				{
					quantum = null;
					result = HardCodedPurity.getInstance().getReturnValueForPureMethod(method);
				}
				else if(HardCodedPurity.getInstance().isThisChangingMethod(method))
				{
					DataValue receiver = ArrayUtil.getFirstElement(data);
					if(receiver == DataValue.ThisDataValue)
					{
						quantum = EffectQuantum.ThisChangeQuantum;
					}
					else if(receiver == DataValue.LocalDataValue || receiver == DataValue.OwnedDataValue)
					{
						quantum = null;
					}
					else if(receiver instanceof DataValue.ParameterDataValue)
					{
						quantum = new EffectQuantum.ParamChangeQuantum(((DataValue.ParameterDataValue) receiver).n);
					}
					if(HardCodedPurity.getInstance().isBuilderChainCall(method))
					{
						// mostly to support string concatenation
						result = receiver;
					}
				}
				effects[insnIndex] = quantum;
				return result;
		}
		return null;
	}

	@Override
	public DataValue unaryOperation(AbstractInsnNode insn, DataValue value)
	{

		switch(insn.getOpcode())
		{
			case Opcodes.LNEG:
			case Opcodes.DNEG:
			case Opcodes.I2L:
			case Opcodes.I2D:
			case Opcodes.L2D:
			case Opcodes.F2L:
			case Opcodes.F2D:
			case Opcodes.D2L:
				return DataValue.UnknownDataValue2;
			case Opcodes.GETFIELD:
				FieldInsnNode fieldInsn = ((FieldInsnNode) insn);
				Member method = new Member(fieldInsn.owner, fieldInsn.name, fieldInsn.desc);
				EKey key = new EKey(method, Direction.Volatile, true);
				effects[methodNode.instructions.indexOf(insn)] = new EffectQuantum.FieldReadQuantum(key);
				if(value == DataValue.ThisDataValue && HardCodedPurity.getInstance().isOwnedField(fieldInsn))
				{
					return DataValue.OwnedDataValue;
				}
				else
				{
					return ASMUtils.getSizeFast(fieldInsn.desc) == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
				}
			case Opcodes.CHECKCAST:
				return value;
			case Opcodes.PUTSTATIC:
				int insnIndex = methodNode.instructions.indexOf(insn);
				effects[insnIndex] = EffectQuantum.TopEffectQuantum;
				return DataValue.UnknownDataValue1;
			case Opcodes.NEWARRAY:
			case Opcodes.ANEWARRAY:
				return DataValue.LocalDataValue;
			default:
				return DataValue.UnknownDataValue1;
		}
	}

	@Override
	public DataValue ternaryOperation(AbstractInsnNode insn, DataValue value1, DataValue value2, DataValue value3)
	{
		int insnIndex = methodNode.instructions.indexOf(insn);
		effects[insnIndex] = getChangeQuantum(value1);
		return DataValue.UnknownDataValue1;
	}

	@Override
	public void returnOperation(AbstractInsnNode insn, DataValue value, DataValue expected)
	{
		if(insn.getOpcode() == Opcodes.ARETURN)
		{
			if(returnValue == null)
			{
				returnValue = value;
			}
			else if(!returnValue.equals(value))
			{
				returnValue = DataValue.UnknownDataValue1;
			}
		}
	}

	@Override
	public DataValue merge(DataValue v1, DataValue v2)
	{
		if(v1.equals(v2))
		{
			return v1;
		}
		else
		{
			int size = Math.min(v1.getSize(), v2.getSize());
			return size == 1 ? DataValue.UnknownDataValue1 : DataValue.UnknownDataValue2;
		}
	}
}

