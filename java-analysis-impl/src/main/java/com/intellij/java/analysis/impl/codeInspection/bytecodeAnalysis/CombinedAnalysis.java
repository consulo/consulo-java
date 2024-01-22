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
import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph;
import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.MethodNode;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;
import consulo.internal.org.objectweb.asm.tree.analysis.Frame;
import one.util.streamex.EntryStream;

import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.*;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.CombinedData.*;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.Direction.*;
import static consulo.internal.org.objectweb.asm.Opcodes.*;


// specialized class for analyzing methods without branching in single pass
final class CombinedAnalysis
{

	private final ControlFlowGraph controlFlow;
	private final Member method;
	private final CombinedInterpreter interpreter;
	private BasicValue returnValue;
	private boolean exception;
	private final MethodNode methodNode;

	CombinedAnalysis(Member method, ControlFlowGraph controlFlow, Set<Member> staticFields)
	{
		this.method = method;
		this.controlFlow = controlFlow;
		methodNode = controlFlow.methodNode;
		interpreter = new CombinedInterpreter(methodNode.instructions, Type.getArgumentTypes(methodNode.desc).length, staticFields);
	}

	final void analyze() throws AnalyzerException
	{
		Frame<BasicValue> frame = createStartFrame();
		int insnIndex = 0;

		while(true)
		{
			AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
			switch(insnNode.getType())
			{
				case AbstractInsnNode.LABEL:
				case AbstractInsnNode.LINE:
				case AbstractInsnNode.FRAME:
					insnIndex = controlFlow.transitions[insnIndex][0];
					break;
				default:
					switch(insnNode.getOpcode())
					{
						case ATHROW:
							exception = true;
							return;
						case ARETURN:
						case IRETURN:
						case LRETURN:
						case FRETURN:
						case DRETURN:
							returnValue = frame.pop();
							return;
						case RETURN:
							// nothing to return
							return;
						default:
							frame.execute(insnNode, interpreter);
							insnIndex = controlFlow.transitions[insnIndex][0];
					}
			}
		}
	}

	final Equation notNullParamEquation(int i, boolean stable)
	{
		final EKey key = new EKey(method, new Direction.In(i, false), stable);
		final Result result;
		if(interpreter.dereferencedParams[i])
		{
			result = Value.NotNull;
		}
		else
		{
			Set<CombinedData.ParamKey> calls = interpreter.parameterFlow[i];
			if(calls == null || calls.isEmpty())
			{
				result = Value.Top;
			}
			else
			{
				Set<EKey> keys = new HashSet<>();
				for(CombinedData.ParamKey pk : calls)
				{
					keys.add(new EKey(pk.method, new Direction.In(pk.i, false), pk.stable));
				}
				result = new Pending(Set.of(new Component(Value.Top, keys)));
			}
		}
		return new Equation(key, result);
	}

	final Equation nullableParamEquation(int i, boolean stable)
	{
		final EKey key = new EKey(method, new In(i, true), stable);
		final Result result;
		if(interpreter.dereferencedParams[i] || interpreter.notNullableParams[i] || returnValue instanceof NthParamValue && ((NthParamValue) returnValue).n == i)
		{
			result = Value.Top;
		}
		else
		{
			Set<ParamKey> calls = interpreter.parameterFlow[i];
			if(calls == null || calls.isEmpty())
			{
				result = Value.Null;
			}
			else
			{
				Set<Component> sum = new HashSet<>();
				for(ParamKey pk : calls)
				{
					sum.add(new Component(Value.Top, Collections.singleton(new EKey(pk.method, new In(pk.i, true), pk.stable))));
				}
				result = new Pending(sum);
			}
		}
		return new Equation(key, result);
	}

	@Nullable
	final Equation contractEquation(int i, Value inValue, boolean stable)
	{
		final InOut direction = new InOut(i, inValue);
		final EKey key = new EKey(method, direction, stable);
		final Result result;
		if(exception || (inValue == Value.Null && interpreter.dereferencedParams[i]))
		{
			result = Value.Bot;
		}
		else if(FalseValue == returnValue)
		{
			result = Value.False;
		}
		else if(TrueValue == returnValue)
		{
			result = Value.True;
		}
		else if(returnValue instanceof TrackableNullValue)
		{
			result = Value.Null;
		}
		else if(returnValue instanceof NotNullValue || ThisValue == returnValue)
		{
			result = Value.NotNull;
		}
		else if(returnValue instanceof NthParamValue && ((NthParamValue) returnValue).n == i)
		{
			result = inValue;
		}
		else if(returnValue instanceof TrackableCallValue)
		{
			TrackableCallValue call = (TrackableCallValue) returnValue;
			Set<EKey> keys = call.getKeysForParameter(i, direction);
			if(ASMUtils.isReferenceType(call.getType()))
			{
				keys.add(new EKey(call.method, Out, call.stableCall));
			}
			if(keys.isEmpty())
			{
				return null;
			}
			else
			{
				result = new Pending(Set.of(new Component(Value.Top, keys)));
			}
		}
		else
		{
			return null;
		}
		return new Equation(key, result);
	}

	@jakarta.annotation.Nullable
	final Equation failEquation(boolean stable)
	{
		final EKey key = new EKey(method, Throw, stable);
		final Result result;
		if(exception)
		{
			result = Value.Fail;
		}
		else if(!interpreter.calls.isEmpty())
		{
			Set<EKey> keys =
					interpreter.calls.stream().map(call -> new EKey(call.method, Throw, call.stableCall)).collect(Collectors.toSet());
			result = new Pending(Set.of(new Component(Value.Top, keys)));
		}
		else
		{
			return null;
		}
		return new Equation(key, result);
	}

	@jakarta.annotation.Nullable
	final Equation failEquation(int i, Value inValue, boolean stable)
	{
		final InThrow direction = new InThrow(i, inValue);
		final EKey key = new EKey(method, direction, stable);
		final Result result;
		if(exception)
		{
			result = Value.Fail;
		}
		else if(!interpreter.calls.isEmpty())
		{
			Set<EKey> keys = new HashSet<>();
			for(TrackableCallValue call : interpreter.calls)
			{
				keys.addAll(call.getKeysForParameter(i, direction));
				keys.add(new EKey(call.method, Throw, call.stableCall));
			}
			result = new Pending(Set.of(new Component(Value.Top, keys)));
		}
		else
		{
			return null;
		}
		return new Equation(key, result);
	}

	@jakarta.annotation.Nullable
	final Equation outContractEquation(boolean stable)
	{
		return outEquation(exception, method, returnValue, stable);
	}

	final List<Equation> staticFieldEquations()
	{
		return EntryStream.of(interpreter.staticFields)
				.removeValues(v -> v == BasicValue.UNINITIALIZED_VALUE)
				.mapKeyValue((field, value) -> outEquation(exception, field, value, true))
				.nonNull()
				.toList();
	}

	@jakarta.annotation.Nullable
	private static Equation outEquation(boolean exception, Member member, BasicValue returnValue, boolean stable)
	{
		final EKey key = new EKey(member, Out, stable);
		final Result result;
		if(exception)
		{
			result = Value.Bot;
		}
		else if(FalseValue == returnValue)
		{
			result = Value.False;
		}
		else if(TrueValue == returnValue)
		{
			result = Value.True;
		}
		else if(returnValue instanceof TrackableNullValue)
		{
			result = Value.Null;
		}
		else if(returnValue instanceof NotNullValue || returnValue == ThisValue)
		{
			result = Value.NotNull;
		}
		else if(returnValue instanceof TrackableCallValue)
		{
			TrackableCallValue call = (TrackableCallValue) returnValue;
			EKey callKey = new EKey(call.method, Out, call.stableCall);
			Set<EKey> keys = Set.of(callKey);
			result = new Pending(Set.of(new Component(Value.Top, keys)));
		}
		else
		{
			return null;
		}
		return new Equation(key, result);
	}

	final Equation nullableResultEquation(boolean stable)
	{
		final EKey key = new EKey(method, NullableOut, stable);
		final Result result;
		if(exception ||
				returnValue instanceof Trackable && interpreter.dereferencedValues[((Trackable) returnValue).getOriginInsnIndex()])
		{
			result = Value.Bot;
		}
		else if(returnValue instanceof TrackableCallValue)
		{
			TrackableCallValue call = (TrackableCallValue) returnValue;
			EKey callKey = new EKey(call.method, NullableOut, call.stableCall || call.thisCall);
			Set<EKey> keys = Set.of(callKey);
			result = new Pending(Set.of(new Component(Value.Null, keys)));
		}
		else if(returnValue instanceof TrackableNullValue)
		{
			result = Value.Null;
		}
		else
		{
			result = Value.Bot;
		}
		return new Equation(key, result);
	}

	final Frame<BasicValue> createStartFrame()
	{
		Frame<BasicValue> frame = new Frame<>(methodNode.maxLocals, methodNode.maxStack);
		Type returnType = Type.getReturnType(methodNode.desc);
		BasicValue returnValue = Type.VOID_TYPE.equals(returnType) ? null : new BasicValue(returnType);
		frame.setReturn(returnValue);

		Type[] args = Type.getArgumentTypes(methodNode.desc);
		int local = 0;
		if((methodNode.access & ACC_STATIC) == 0)
		{
			frame.setLocal(local++, ThisValue);
		}
		for(int i = 0; i < args.length; i++)
		{
			BasicValue value = new NthParamValue(args[i], i);
			frame.setLocal(local++, value);
			if(args[i].getSize() == 2)
			{
				frame.setLocal(local++, BasicValue.UNINITIALIZED_VALUE);
			}
		}
		while(local < methodNode.maxLocals)
		{
			frame.setLocal(local++, BasicValue.UNINITIALIZED_VALUE);
		}
		return frame;
	}
}
