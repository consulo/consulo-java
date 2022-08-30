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

import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph;
import consulo.internal.org.objectweb.asm.Type;
import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.JumpInsnNode;
import consulo.internal.org.objectweb.asm.tree.MethodNode;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;
import consulo.internal.org.objectweb.asm.tree.analysis.Frame;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.FalseValue;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.TrueValue;
import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.CombinedData.ThisValue;
import static consulo.internal.org.objectweb.asm.Opcodes.*;

final class NegationAnalysis
{

	private final ControlFlowGraph controlFlow;
	private final Member method;
	private final NegationInterpreter interpreter;
	private final MethodNode methodNode;

	private CombinedData.TrackableCallValue conditionValue;
	private BasicValue trueBranchValue;
	private BasicValue falseBranchValue;

	NegationAnalysis(Member method, ControlFlowGraph controlFlow)
	{
		this.method = method;
		this.controlFlow = controlFlow;
		methodNode = controlFlow.methodNode;
		interpreter = new NegationInterpreter(methodNode.instructions);
	}

	private static void checkAssertion(boolean assertion) throws NegationAnalysisFailedException
	{
		if(!assertion)
		{
			throw new NegationAnalysisFailedException();
		}
	}

	final void analyze() throws AnalyzerException, NegationAnalysisFailedException
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
						case IFEQ:
						case IFNE:
							BasicValue conValue = popValue(frame);
							checkAssertion(conValue instanceof CombinedData.TrackableCallValue);
							frame.execute(insnNode, interpreter);
							conditionValue = (CombinedData.TrackableCallValue) conValue;
							int jumpIndex = methodNode.instructions.indexOf(((JumpInsnNode) insnNode).label);
							int nextIndex = insnIndex + 1;
							proceedBranch(frame, jumpIndex, IFNE == insnNode.getOpcode());
							proceedBranch(frame, nextIndex, IFEQ == insnNode.getOpcode());
							checkAssertion(FalseValue == trueBranchValue);
							checkAssertion(TrueValue == falseBranchValue);
							return;
						default:
							frame.execute(insnNode, interpreter);
							insnIndex = controlFlow.transitions[insnIndex][0];
					}
			}
		}
	}

	private void proceedBranch(Frame<BasicValue> startFrame, int startIndex, boolean branchValue)
			throws NegationAnalysisFailedException, AnalyzerException
	{

		Frame<BasicValue> frame = new Frame<>(startFrame);
		int insnIndex = startIndex;

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
					if(insnNode.getOpcode() == IRETURN)
					{
						BasicValue returnValue = frame.pop();
						if(branchValue)
						{
							trueBranchValue = returnValue;
						}
						else
						{
							falseBranchValue = returnValue;
						}
						return;
					}
					else
					{
						checkAssertion(controlFlow.transitions[insnIndex].length == 1);
						frame.execute(insnNode, interpreter);
						insnIndex = controlFlow.transitions[insnIndex][0];
					}
			}
		}
	}

	final Equation contractEquation(int i, Value inValue, boolean stable)
	{
		final EKey key = new EKey(method, new Direction.InOut(i, inValue), stable);
		final Result result;
		HashSet<EKey> keys = new HashSet<>();
		for(int argI = 0; argI < conditionValue.args.size(); argI++)
		{
			BasicValue arg = conditionValue.args.get(argI);
			if(arg instanceof AbstractValues.NthParamValue)
			{
				AbstractValues.NthParamValue npv = (AbstractValues.NthParamValue) arg;
				if(npv.n == i)
				{
					keys.add(new EKey(conditionValue.method, new Direction.InOut(argI, inValue), conditionValue.stableCall, true));
				}
			}
		}
		if(keys.isEmpty())
		{
			result = Value.Top;
		}
		else
		{
			result = new Pending(Set.of(new Component(Value.Top, keys)));
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
			BasicValue value = new AbstractValues.NthParamValue(args[i], i);
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

	private static BasicValue popValue(Frame<BasicValue> frame)
	{
		return frame.getStack(frame.getStackSize() - 1);
	}
}
