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

import java.util.HashSet;
import consulo.internal.org.objectweb.asm.Handle;
import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.*;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicInterpreter;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;
import one.util.streamex.StreamEx;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.*;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.CombinedData.ThisValue;

final class CombinedInterpreter extends BasicInterpreter
{
	// Parameters dereferenced during execution of a method, tracked by parameter's indices.
	// Dereferenced parameters are @NotNull.
	final boolean[] dereferencedParams;
	// Parameters, that are written to something or passed to an interface methods.
	// This parameters cannot be @Nullable.
	final boolean[] notNullableParams;
	// parameterFlow(i) for i-th parameter stores a set parameter positions it is passed to
	// parameter is @NotNull if any of its usages are @NotNull
	final Set<CombinedData.ParamKey>[] parameterFlow;

	// Trackable values that were dereferenced during execution of a method
	// Values are are identified by `origin` index
	final boolean[] dereferencedValues;

	final List<CombinedData.TrackableCallValue> calls = new ArrayList<>();

	final Map<Member, BasicValue> staticFields;

	private final InsnList insns;

	CombinedInterpreter(InsnList insns,
						int arity,
						Set<Member> staticFields)
	{
		super(Opcodes.API_VERSION);
		dereferencedParams = new boolean[arity];
		notNullableParams = new boolean[arity];
		parameterFlow = new Set[arity];
		this.insns = insns;
		dereferencedValues = new boolean[insns.size()];
		this.staticFields = StreamEx.of(staticFields).cross(BasicValue.UNINITIALIZED_VALUE).toMap();
	}

	private int insnIndex(AbstractInsnNode insn)
	{
		return insns.indexOf(insn);
	}

	private static BasicValue track(int origin, BasicValue basicValue)
	{
		return basicValue == null ? null : new CombinedData.TrackableValue(origin, basicValue.getType());
	}

	@Override
	public BasicValue newOperation(AbstractInsnNode insn) throws AnalyzerException
	{
		int origin = insnIndex(insn);
		switch(insn.getOpcode())
		{
			case ICONST_0:
				return FalseValue;
			case ICONST_1:
				return TrueValue;
			case ACONST_NULL:
				return new CombinedData.TrackableNullValue(origin);
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
		return track(origin, super.newOperation(insn));
	}

	@Override
	public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException
	{
		int origin = insnIndex(insn);
		switch(insn.getOpcode())
		{
			case GETFIELD:
			case ARRAYLENGTH:
			case MONITORENTER:
				if(value instanceof NthParamValue)
				{
					dereferencedParams[((NthParamValue) value).n] = true;
				}
				if(value instanceof CombinedData.Trackable)
				{
					dereferencedValues[((CombinedData.Trackable) value).getOriginInsnIndex()] = true;
				}
				return track(origin, super.unaryOperation(insn, value));
			case PUTSTATIC:
				if(!staticFields.isEmpty())
				{
					FieldInsnNode node = (FieldInsnNode) insn;
					Member field = new Member(node.owner, node.name, node.desc);
					staticFields.computeIfPresent(field, (f, v) -> value);
				}
				break;
			case CHECKCAST:
				if(value instanceof NthParamValue)
				{
					return new NthParamValue(Type.getObjectType(((TypeInsnNode) insn).desc), ((NthParamValue) value).n);
				}
				break;
			case NEWARRAY:
			case ANEWARRAY:
				return new NotNullValue(super.unaryOperation(insn, value).getType());
			default:
		}
		return track(origin, super.unaryOperation(insn, value));
	}

	@Override
	public BasicValue binaryOperation(AbstractInsnNode insn, BasicValue value1, BasicValue value2) throws AnalyzerException
	{
		switch(insn.getOpcode())
		{
			case PUTFIELD:
				if(value1 instanceof NthParamValue)
				{
					dereferencedParams[((NthParamValue) value1).n] = true;
				}
				if(value1 instanceof CombinedData.Trackable)
				{
					dereferencedValues[((CombinedData.Trackable) value1).getOriginInsnIndex()] = true;
				}
				if(value2 instanceof NthParamValue)
				{
					notNullableParams[((NthParamValue) value2).n] = true;
				}
				break;
			case IALOAD:
			case LALOAD:
			case FALOAD:
			case DALOAD:
			case AALOAD:
			case BALOAD:
			case CALOAD:
			case SALOAD:
				if(value1 instanceof NthParamValue)
				{
					dereferencedParams[((NthParamValue) value1).n] = true;
				}
				if(value1 instanceof CombinedData.Trackable)
				{
					dereferencedValues[((CombinedData.Trackable) value1).getOriginInsnIndex()] = true;
				}
				break;
			default:
		}
		return track(insnIndex(insn), super.binaryOperation(insn, value1, value2));
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
				if(value1 instanceof NthParamValue)
				{
					dereferencedParams[((NthParamValue) value1).n] = true;
				}
				if(value1 instanceof CombinedData.Trackable)
				{
					dereferencedValues[((CombinedData.Trackable) value1).getOriginInsnIndex()] = true;
				}
				break;
			case AASTORE:
				if(value1 instanceof NthParamValue)
				{
					dereferencedParams[((NthParamValue) value1).n] = true;
				}
				if(value1 instanceof CombinedData.Trackable)
				{
					dereferencedValues[((CombinedData.Trackable) value1).getOriginInsnIndex()] = true;
				}
				if(value3 instanceof NthParamValue)
				{
					notNullableParams[((NthParamValue) value3).n] = true;
				}
				break;
			default:
		}
		return null;
	}

	@Override
	public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException
	{
		int opCode = insn.getOpcode();
		int origin = insnIndex(insn);

		switch(opCode)
		{
			case INVOKESTATIC:
			case INVOKESPECIAL:
			case INVOKEVIRTUAL:
			case INVOKEINTERFACE:
			{
				MethodInsnNode mNode = (MethodInsnNode) insn;
				Member method = new Member(mNode.owner, mNode.name, mNode.desc);
				CombinedData.TrackableCallValue value = methodCall(opCode, origin, method, values);
				calls.add(value);
				return value;
			}
			case INVOKEDYNAMIC:
			{
				InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
				if(ClassDataIndexer.STRING_CONCAT_FACTORY.equals(indy.bsm.getOwner()))
				{
					return new NotNullValue(Type.getReturnType(indy.desc));
				}
				LambdaIndy lambda = LambdaIndy.from(indy);
				if(lambda == null)
					break;
				int targetOpCode = lambda.getAssociatedOpcode();
				if(targetOpCode == -1)
					break;
				methodCall(targetOpCode, origin, lambda.getMethod(), lambda.getLambdaMethodArguments(values, this::newValue));
				return new NotNullValue(lambda.getFunctionalInterfaceType());
			}
			case MULTIANEWARRAY:
				return new NotNullValue(super.naryOperation(insn, values).getType());
			default:
		}
		return track(origin, super.naryOperation(insn, values));
	}

	@Nonnull
	private CombinedData.TrackableCallValue methodCall(int opCode, int origin, Member method, List<? extends BasicValue> values)
	{
		Type retType = Type.getReturnType(method.methodDesc);
		boolean stable = opCode == INVOKESTATIC || opCode == INVOKESPECIAL;
		boolean thisCall = false;
		if(opCode != INVOKESTATIC)
		{
			BasicValue receiver = values.remove(0);
			if(receiver instanceof NthParamValue)
			{
				dereferencedParams[((NthParamValue) receiver).n] = true;
			}
			if(receiver instanceof CombinedData.Trackable)
			{
				dereferencedValues[((CombinedData.Trackable) receiver).getOriginInsnIndex()] = true;
			}
			thisCall = receiver == ThisValue;
		}

		for(int i = 0; i < values.size(); i++)
		{
			if(values.get(i) instanceof NthParamValue)
			{
				int n = ((NthParamValue) values.get(i)).n;
				if(opCode == INVOKEINTERFACE)
				{
					notNullableParams[n] = true;
				}
				else
				{
					Set<CombinedData.ParamKey> npKeys = parameterFlow[n];
					if(npKeys == null)
					{
						npKeys = new HashSet<>();
						parameterFlow[n] = npKeys;
					}
					npKeys.add(new CombinedData.ParamKey(method, i, stable));
				}
			}
		}
		return new CombinedData.TrackableCallValue(origin, retType, method, values, stable, thisCall);
	}
}
