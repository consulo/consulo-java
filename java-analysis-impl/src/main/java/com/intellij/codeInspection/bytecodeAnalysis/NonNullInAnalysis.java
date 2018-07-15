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
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.Identity;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.NPE;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.Return;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.join;
import static com.intellij.codeInspection.bytecodeAnalysis.PResults.meet;
import static org.objectweb.asm.Opcodes.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ASMUtils;
import com.intellij.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph;
import com.intellij.codeInspection.bytecodeAnalysis.asm.RichControlFlow;

class NonNullInAnalysis extends Analysis<PResults.PResult>
{
	final private PendingAction[] pendingActions;
	private final PResults.PResult[] results;
	private final NotNullInterpreter interpreter = new NotNullInterpreter();

	// Flag saying that at some branch NPE was found. Used later as an evidence that this param is *NOT* @Nullable (optimization).
	boolean possibleNPE;

	protected NonNullInAnalysis(RichControlFlow richControlFlow, Direction direction, boolean stable, PendingAction[] pendingActions, PResults.PResult[] results)
	{
		super(richControlFlow, direction, stable);
		this.pendingActions = pendingActions;
		this.results = results;
	}

	PResults.PResult combineResults(PResults.PResult delta, int[] subResults) throws AnalyzerException
	{
		PResults.PResult result = Identity;
		for(int subResult : subResults)
		{
			result = join(result, results[subResult]);
		}
		return meet(delta, result);
	}

	@Nonnull
	Equation mkEquation(PResults.PResult result)
	{
		if(Identity == result || Return == result)
		{
			return new Equation(aKey, new Final(Value.Top));
		}
		else if(NPE == result)
		{
			return new Equation(aKey, new Final(Value.NotNull));
		}
		else
		{
			PResults.ConditionalNPE condNpe = (PResults.ConditionalNPE) result;
			Set<Product> components = new HashSet<Product>();
			for(Set<Key> prod : condNpe.sop)
			{
				components.add(new Product(Value.Top, prod));
			}
			return new Equation(aKey, new Pending(components));
		}
	}

	private int id;
	private Frame<BasicValue> nextFrame;
	private PResults.PResult subResult;

	@Nonnull
	protected Equation analyze() throws AnalyzerException
	{
		pendingPush(new ProceedState(createStartState()));
		int steps = 0;
		while(pendingTop > 0 && earlyResult == null)
		{
			steps++;
			if(steps >= STEPS_LIMIT)
			{
				throw new AnalyzerException(null, "limit is reached, steps: " + steps + " in method " + method);
			}
			PendingAction action = pendingActions[--pendingTop];
			if(action instanceof MakeResult)
			{
				MakeResult makeResult = (MakeResult) action;
				PResults.PResult result = combineResults(makeResult.subResult, makeResult.indices);
				State state = makeResult.state;
				int insnIndex = state.conf.insnIndex;
				results[state.index] = result;
				addComputed(insnIndex, state);
			}
			else if(action instanceof ProceedState)
			{
				ProceedState proceedState = (ProceedState) action;
				State state = proceedState.state;
				int insnIndex = state.conf.insnIndex;
				Conf conf = state.conf;
				List<Conf> history = state.history;

				boolean fold = false;
				if(dfsTree.loopEnters[insnIndex])
				{
					for(Conf prev : history)
					{
						if(AbstractValues.isInstance(conf, prev))
						{
							fold = true;
							break;
						}
					}
				}
				if(fold)
				{
					results[state.index] = Identity;
					addComputed(insnIndex, state);
				}
				else
				{
					State baseState = null;
					List<State> thisComputed = computed[insnIndex];
					if(thisComputed != null)
					{
						for(State prevState : thisComputed)
						{
							if(stateEquiv(state, prevState))
							{
								baseState = prevState;
								break;
							}
						}
					}
					if(baseState != null)
					{
						results[state.index] = results[baseState.index];
					}
					else
					{
						// the main call
						processState(state);
					}

				}
			}
		}
		if(earlyResult != null)
		{
			return mkEquation(earlyResult);
		}
		else
		{
			return mkEquation(results[0]);
		}
	}

	private void processState(State state) throws AnalyzerException
	{
		int stateIndex = state.index;
		Conf conf = state.conf;
		int insnIndex = conf.insnIndex;
		List<Conf> history = state.history;
		boolean taken = state.taken;
		Frame<BasicValue> frame = conf.frame;
		AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
		List<Conf> nextHistory = dfsTree.loopEnters[insnIndex] ? append(history, conf) : history;
		boolean hasCompanions = state.hasCompanions;
		execute(frame, insnNode);

		boolean notEmptySubResult = subResult != Identity;

		if(subResult == NPE)
		{
			results[stateIndex] = NPE;
			possibleNPE = true;
			addComputed(insnIndex, state);
			return;
		}

		int opcode = insnNode.getOpcode();
		switch(opcode)
		{
			case ARETURN:
			case IRETURN:
			case LRETURN:
			case FRETURN:
			case DRETURN:
			case RETURN:
				if(!hasCompanions)
				{
					earlyResult = Return;
				}
				else
				{
					results[stateIndex] = Return;
					addComputed(insnIndex, state);
				}
				return;
			default:
		}

		if(opcode == ATHROW)
		{
			if(taken)
			{
				results[stateIndex] = NPE;
				possibleNPE = true;
			}
			else
			{
				results[stateIndex] = Identity;
			}
			addComputed(insnIndex, state);
			return;
		}

		if(opcode == IFNONNULL && popValue(frame) instanceof AbstractValues.ParamValue)
		{
			int nextInsnIndex = insnIndex + 1;
			State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
			pendingPush(new MakeResult(state, subResult, new int[]{nextState.index}));
			pendingPush(new ProceedState(nextState));
			return;
		}

		if(opcode == IFNULL && popValue(frame) instanceof AbstractValues.ParamValue)
		{
			int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode) insnNode).label);
			State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
			pendingPush(new MakeResult(state, subResult, new int[]{nextState.index}));
			pendingPush(new ProceedState(nextState));
			return;
		}

		if(opcode == IFEQ && popValue(frame) == InstanceOfCheckValue)
		{
			int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode) insnNode).label);
			State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
			pendingPush(new MakeResult(state, subResult, new int[]{nextState.index}));
			pendingPush(new ProceedState(nextState));
			return;
		}

		if(opcode == IFNE && popValue(frame) == InstanceOfCheckValue)
		{
			int nextInsnIndex = insnIndex + 1;
			State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, hasCompanions || notEmptySubResult);
			pendingPush(new MakeResult(state, subResult, new int[]{nextState.index}));
			pendingPush(new ProceedState(nextState));
			return;
		}

		// general case
		int[] nextInsnIndices = controlFlow.transitions[insnIndex];
		int[] subIndices = new int[nextInsnIndices.length];
		for(int i = 0; i < nextInsnIndices.length; i++)
		{
			subIndices[i] = ++id;
		}
		pendingPush(new MakeResult(state, subResult, subIndices));
		for(int i = 0; i < nextInsnIndices.length; i++)
		{
			int nextInsnIndex = nextInsnIndices[i];
			Frame<BasicValue> nextFrame1 = nextFrame;
			if(controlFlow.errors[nextInsnIndex] && controlFlow.errorTransitions.contains(new ControlFlowGraph.Edge(insnIndex, nextInsnIndex)))
			{
				nextFrame1 = new Frame<BasicValue>(frame);
				nextFrame1.clearStack();
				nextFrame1.push(ASMUtils.THROWABLE_VALUE);
			}
			pendingPush(new ProceedState(new State(subIndices[i], new Conf(nextInsnIndex, nextFrame1), nextHistory, taken, hasCompanions || notEmptySubResult)));
		}

	}

	private int pendingTop;

	private void pendingPush(PendingAction action) throws AnalyzerException
	{
		if(pendingTop >= STEPS_LIMIT)
		{
			throw new AnalyzerException(null, "limit is reached in method " + method);
		}
		pendingActions[pendingTop++] = action;
	}

	private void execute(Frame<BasicValue> frame, AbstractInsnNode insnNode) throws AnalyzerException
	{
		switch(insnNode.getType())
		{
			case AbstractInsnNode.LABEL:
			case AbstractInsnNode.LINE:
			case AbstractInsnNode.FRAME:
				nextFrame = frame;
				subResult = Identity;
				break;
			default:
				nextFrame = new Frame<BasicValue>(frame);
				interpreter.reset(false);
				nextFrame.execute(insnNode, interpreter);
				subResult = interpreter.getSubResult();
		}
	}
}
