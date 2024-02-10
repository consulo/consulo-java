package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.Direction.ParamValueBasedDirection;
import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph.Edge;
import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm.RichControlFlow;
import consulo.application.progress.ProgressManager;
import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.JumpInsnNode;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;
import consulo.internal.org.objectweb.asm.tree.analysis.Frame;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.List;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.*;
import static consulo.internal.org.objectweb.asm.Opcodes.*;

abstract class ContractAnalysis extends Analysis<Result>
{
	static final ResultUtil resultUtil = new ResultUtil(new ELattice<>(Value.Bot, Value.Top));

	final private State[] pending;
	final InOutInterpreter interpreter;
	final Value inValue;
	private final int generalizeShift;
	Result internalResult;
	boolean unsureOnly = true;
	private int id;
	private int pendingTop;

	protected ContractAnalysis(RichControlFlow richControlFlow, Direction direction, boolean[] resultOrigins, boolean stable, State[] pending)
	{
		super(richControlFlow, direction, stable);
		this.pending = pending;
		interpreter = new InOutInterpreter(direction, richControlFlow.controlFlow.methodNode.instructions, resultOrigins);
		inValue = direction instanceof ParamValueBasedDirection ? ((ParamValueBasedDirection) direction).inValue : null;
		generalizeShift = (methodNode.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
		internalResult = Value.Bot;
	}

	@Nonnull
	Equation mkEquation(Result res)
	{
		return new Equation(aKey, res);
	}

	static Result checkLimit(Result result) throws AnalyzerException
	{
		if(result instanceof Pending)
		{
			int size = Arrays.stream(((Pending) result).delta).mapToInt(prod -> prod.ids.length).sum();
			if(size > Analysis.EQUATION_SIZE_LIMIT)
			{
				throw new AnalyzerException(null, "Equation size is too big");
			}
		}
		return result;
	}

	@Override
	@Nonnull
	protected Equation analyze() throws AnalyzerException
	{
		pendingPush(createStartState());
		int steps = 0;
		while(pendingTop > 0 && earlyResult == null)
		{
			steps++;
			TooComplexException.check(method, steps);
			if(steps % 128 == 0)
			{
				ProgressManager.checkCanceled();
			}
			State state = pending[--pendingTop];
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
				if(baseState == null)
				{
					processState(state);
				}
			}
		}
		if(earlyResult != null)
		{
			return mkEquation(earlyResult);
		}
		else if(unsureOnly)
		{
			// We are not sure whether exceptional paths were actually taken or not
			// probably they handle exceptions which can never be thrown before dereference occurs
			return mkEquation(Value.Bot);
		}
		else
		{
			return mkEquation(internalResult);
		}
	}

	void processState(State state) throws AnalyzerException
	{
		Conf preConf = state.conf;
		int insnIndex = preConf.insnIndex;
		boolean loopEnter = dfsTree.loopEnters[insnIndex];
		Conf conf = loopEnter ? generalize(preConf) : preConf;
		List<Conf> history = state.history;
		boolean taken = state.taken;
		Frame<BasicValue> frame = conf.frame;
		AbstractInsnNode insnNode = methodNode.instructions.get(insnIndex);
		List<Conf> nextHistory = loopEnter ? append(history, conf) : history;
		Frame<BasicValue> nextFrame = execute(frame, insnNode);

		addComputed(insnIndex, state);

		int opcode = insnNode.getOpcode();

		if(interpreter.deReferenced && controlFlow.npeTransitions.containsKey(insnIndex))
		{
			interpreter.deReferenced = false;
			int npeTarget = controlFlow.npeTransitions.getInt(insnIndex);
			for(int nextInsnIndex : controlFlow.transitions[insnIndex])
			{
				if(!controlFlow.errorTransitions.contains(new Edge(insnIndex, nextInsnIndex)))
				{
					continue;
				}
				Frame<BasicValue> nextFrame1 = createCatchFrame(frame);
				boolean unsure = state.unsure || nextInsnIndex != npeTarget;
				pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame1), nextHistory, taken, false, unsure));
			}
			return;
		}

		if(handleReturn(frame, opcode, state.unsure))
		{
			return;
		}

		if(opcode == IFNONNULL && popValue(frame) instanceof ParamValue)
		{
			int nextInsnIndex = inValue == Value.Null ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode) insnNode).label);
			State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
			pendingPush(nextState);
			return;
		}

		if(opcode == IFNULL && popValue(frame) instanceof ParamValue)
		{
			int nextInsnIndex = inValue == Value.NotNull ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode) insnNode).label);
			State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
			pendingPush(nextState);
			return;
		}

		if(opcode == IFEQ && popValue(frame) instanceof ParamValue)
		{
			int nextInsnIndex = inValue == Value.True ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode) insnNode).label);
			State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
			pendingPush(nextState);
			return;
		}

		if(opcode == IFNE && popValue(frame) instanceof ParamValue)
		{
			int nextInsnIndex = inValue == Value.False ? insnIndex + 1 : methodNode.instructions.indexOf(((JumpInsnNode) insnNode).label);
			State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
			pendingPush(nextState);
			return;
		}

		if(opcode == IFEQ && popValue(frame) == InstanceOfCheckValue && inValue == Value.Null)
		{
			int nextInsnIndex = methodNode.instructions.indexOf(((JumpInsnNode) insnNode).label);
			State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
			pendingPush(nextState);
			return;
		}

		if(opcode == IFNE && popValue(frame) == InstanceOfCheckValue && inValue == Value.Null)
		{
			int nextInsnIndex = insnIndex + 1;
			State nextState = new State(++id, new Conf(nextInsnIndex, nextFrame), nextHistory, true, false, state.unsure);
			pendingPush(nextState);
			return;
		}

		// general case
		for(int nextInsnIndex : controlFlow.transitions[insnIndex])
		{
			Frame<BasicValue> nextFrame1 = nextFrame;
			boolean unsure = state.unsure;
			if(controlFlow.errors[nextInsnIndex] && controlFlow.errorTransitions.contains(new Edge(insnIndex, nextInsnIndex)))
			{
				nextFrame1 = createCatchFrame(frame);
				unsure = true;
			}
			pendingPush(new State(++id, new Conf(nextInsnIndex, nextFrame1), nextHistory, taken, false, unsure));
		}
	}

	abstract boolean handleReturn(Frame<BasicValue> frame, int opcode, boolean unsure) throws AnalyzerException;

	private void pendingPush(State st)
	{
		TooComplexException.check(method, pendingTop);
		pending[pendingTop++] = st;
	}

	private Frame<BasicValue> execute(Frame<BasicValue> frame, AbstractInsnNode insnNode) throws AnalyzerException
	{
		interpreter.deReferenced = false;
		switch(insnNode.getType())
		{
			case AbstractInsnNode.LABEL:
			case AbstractInsnNode.LINE:
			case AbstractInsnNode.FRAME:
				return frame;
			default:
				Frame<BasicValue> nextFrame = new Frame<>(frame);
				nextFrame.execute(insnNode, interpreter);
				return nextFrame;
		}
	}

	private Conf generalize(Conf conf)
	{
		Frame<BasicValue> frame = new Frame<>(conf.frame);
		for(int i = generalizeShift; i < frame.getLocals(); i++)
		{
			BasicValue value = frame.getLocal(i);
			Class<?> valueClass = value.getClass();
			if(valueClass != BasicValue.class && valueClass != ParamValue.class)
			{
				frame.setLocal(i, new BasicValue(value.getType()));
			}
		}

		BasicValue[] stack = new BasicValue[frame.getStackSize()];
		for(int i = 0; i < frame.getStackSize(); i++)
		{
			stack[i] = frame.getStack(i);
		}
		frame.clearStack();

		for(BasicValue value : stack)
		{
			Class<?> valueClass = value.getClass();
			if(valueClass != BasicValue.class && valueClass != ParamValue.class)
			{
				frame.push(new BasicValue(value.getType()));
			}
			else
			{
				frame.push(value);
			}
		}

		return new Conf(conf.insnIndex, frame);
	}
}