package com.intellij.java.analysis.impl.codeInspection.dataFlow;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * from kotlin
 */
public class ExitFinallyTransfer implements TransferTarget
{
	private Trap.EnterFinally enterFinally;

	public ExitFinallyTransfer(Trap.EnterFinally enterFinally)
	{
		this.enterFinally = enterFinally;
	}

	@Override
	public Set<Integer> getPossibleTargets()
	{
		return enterFinally.backLines
				.stream()
				.flatMap(instruction -> instruction.getPossibleTargetIndices().stream())
				.filter(index -> index != enterFinally.getJumpOffset().getInstructionOffset())
				.collect(Collectors.toSet());
	}

	@Override
	public List<DfaInstructionState> dispatch(DfaMemoryState state, DataFlowRunner runner)
	{
		DfaControlTransferValue pop = (DfaControlTransferValue) state.pop();
		return pop.dispatch(state, runner);
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}

		ExitFinallyTransfer that = (ExitFinallyTransfer) o;

		if(enterFinally != null ? !enterFinally.equals(that.enterFinally) : that.enterFinally != null)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		return enterFinally != null ? enterFinally.hashCode() : 0;
	}

	@Override
	public String toString()
	{
		return "ExitFinallyTransfer{" +
				"enterFinally=" + enterFinally +
				'}';
	}
}
