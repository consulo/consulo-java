package com.intellij.codeInspection.dataFlow;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;

public class StateQueue
{
	private final PriorityQueue<DfaInstructionState> myQueue = new PriorityQueue<DfaInstructionState>();
	private final Set<Pair<Instruction, DfaMemoryState>> mySet = ContainerUtil.newHashSet();

	void offer(DfaInstructionState state)
	{
		if(mySet.add(Pair.create(state.getInstruction(), state.getMemoryState())))
		{
			myQueue.offer(state);
		}
	}

	boolean isEmpty()
	{
		return myQueue.isEmpty();
	}

	List<DfaInstructionState> getNextInstructionStates(Set<Instruction> joinInstructions)
	{
		DfaInstructionState state = myQueue.poll();
		final Instruction instruction = state.getInstruction();
		mySet.remove(Pair.create(instruction, state.getMemoryState()));

		DfaInstructionState next = myQueue.peek();
		if(next == null || next.compareTo(state) != 0)
		{
			return Collections.singletonList(state);
		}

		List<DfaMemoryStateImpl> memoryStates = ContainerUtil.newArrayList();
		memoryStates.add((DfaMemoryStateImpl) state.getMemoryState());
		while(!myQueue.isEmpty() && myQueue.peek().compareTo(state) == 0)
		{
			DfaMemoryState anotherState = myQueue.poll().getMemoryState();
			mySet.remove(Pair.create(instruction, anotherState));
			memoryStates.add((DfaMemoryStateImpl) anotherState);
		}

		if(memoryStates.size() > 1 && joinInstructions.contains(instruction))
		{
			MultiMap<Object, DfaMemoryStateImpl> groups = MultiMap.create();
			for(DfaMemoryStateImpl memoryState : memoryStates)
			{
				groups.putValue(memoryState.getSuperficialKey(), memoryState);
			}

			memoryStates = ContainerUtil.newArrayList();
			for(Map.Entry<Object, Collection<DfaMemoryStateImpl>> entry : groups.entrySet())
			{
				memoryStates.addAll(mergeGroup((List<DfaMemoryStateImpl>) entry.getValue()));
			}

		}

		return ContainerUtil.map(memoryStates, new Function<DfaMemoryStateImpl, DfaInstructionState>()
		{
			@Override
			public DfaInstructionState fun(DfaMemoryStateImpl state)
			{
				return new DfaInstructionState(instruction, state);
			}
		});
	}

	private static List<DfaMemoryStateImpl> mergeGroup(List<DfaMemoryStateImpl> group)
	{
		if(group.size() < 2)
		{
			return group;
		}

		StateMerger merger = new StateMerger();
		while(true)
		{
			List<DfaMemoryStateImpl> nextStates = merger.mergeByFacts(group);
			if(nextStates == null)
			{
				nextStates = merger.mergeByNullability(group);
			}
			if(nextStates == null)
			{
				nextStates = merger.mergeByUnknowns(group);
			}
			if(nextStates == null)
			{
				break;
			}
			group = nextStates;
		}
		return group;
	}
}