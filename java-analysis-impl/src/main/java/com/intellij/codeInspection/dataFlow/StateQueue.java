/*
 * Copyright 2013-2017 consulo.io
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

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
import javax.annotation.Nonnull;

import java.util.*;

class StateQueue
{
	private static final int FORCE_MERGE_THRESHOLD = 100;
	private boolean myWasForciblyMerged;
	private final PriorityQueue<DfaInstructionState> myQueue = new PriorityQueue<>();
	private final Set<Pair<Instruction, DfaMemoryState>> mySet = new HashSet<>();

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

	boolean processAll(@Nonnull Processor<? super DfaInstructionState> processor)
	{
		for(DfaInstructionState state : myQueue)
		{
			if(!processor.process(state))
				return false;
		}
		return true;
	}

	@Nonnull
	List<DfaInstructionState> getNextInstructionStates(Set<Instruction> joinInstructions)
	{
		DfaInstructionState state = myQueue.remove();
		final Instruction instruction = state.getInstruction();
		mySet.remove(Pair.create(instruction, state.getMemoryState()));

		DfaInstructionState next = myQueue.peek();
		if(next == null || next.compareTo(state) != 0)
			return Collections.singletonList(state);

		List<DfaMemoryStateImpl> memoryStates = new ArrayList<>();
		memoryStates.add((DfaMemoryStateImpl) state.getMemoryState());
		while(!myQueue.isEmpty() && myQueue.peek().compareTo(state) == 0)
		{
			DfaMemoryState anotherState = myQueue.poll().getMemoryState();
			mySet.remove(Pair.create(instruction, anotherState));
			memoryStates.add((DfaMemoryStateImpl) anotherState);
		}

		if(memoryStates.size() > 1 && joinInstructions.contains(instruction))
		{
			memoryStates = squash(memoryStates);
		}

		if(memoryStates.size() > 1 && joinInstructions.contains(instruction))
		{
			while(true)
			{
				int beforeSize = memoryStates.size();
				MultiMap<Object, DfaMemoryStateImpl> groups = MultiMap.create();
				for(DfaMemoryStateImpl memoryState : memoryStates)
				{
					groups.putValue(memoryState.getSuperficialKey(), memoryState);
				}

				memoryStates = new ArrayList<>();
				for(Map.Entry<Object, Collection<DfaMemoryStateImpl>> entry : groups.entrySet())
				{
					memoryStates.addAll(mergeGroup((List<DfaMemoryStateImpl>) entry.getValue()));
				}
				if(memoryStates.size() == beforeSize)
					break;
				beforeSize = memoryStates.size();
				if(beforeSize == 1)
					break;
				// If some states were merged it's possible that they could be further squashed
				memoryStates = squash(memoryStates);
				if(memoryStates.size() == beforeSize || memoryStates.size() == 1)
					break;
			}
		}

		memoryStates = forceMerge(memoryStates);

		return ContainerUtil.map(memoryStates, state1 -> new DfaInstructionState(instruction, state1));
	}

	private static List<DfaMemoryStateImpl> squash(List<DfaMemoryStateImpl> states)
	{
		List<DfaMemoryStateImpl> result = new ArrayList<>(states);
		for(Iterator<DfaMemoryStateImpl> iterator = result.iterator(); iterator.hasNext(); )
		{
			DfaMemoryStateImpl left = iterator.next();
			for(DfaMemoryStateImpl right : result)
			{
				ProgressManager.checkCanceled();
				if(right != left && right.isSuperStateOf(left))
				{
					iterator.remove();
					break;
				}
			}
		}
		return result;
	}

	static List<DfaMemoryStateImpl> mergeGroup(List<DfaMemoryStateImpl> group)
	{
		if(group.size() < 2)
		{
			return group;
		}

		StateMerger merger = new StateMerger();
		while(group.size() > 1)
		{
			List<DfaMemoryStateImpl> nextStates = merger.mergeByRanges(group);
			if(nextStates == null)
				nextStates = merger.mergeByFacts(group);
			if(nextStates == null)
				break;
			group = nextStates;
		}
		return group;
	}

	private List<DfaMemoryStateImpl> forceMerge(List<DfaMemoryStateImpl> states)
	{
		if(states.size() < FORCE_MERGE_THRESHOLD)
			return states;
		myWasForciblyMerged = true;
		Collection<List<DfaMemoryStateImpl>> groups = StreamEx.of(states).groupingBy(DfaMemoryStateImpl::getMergeabilityKey).values();
		return StreamEx.of(groups)
				.flatMap(group -> StreamEx.ofSubLists(group, 2)
						.map(pair -> {
							if(pair.size() == 2)
							{
								pair.get(0).merge(pair.get(1));
							}
							return pair.get(0);
						})).distinct().toListAndThen(StateQueue::squash);
	}

	boolean wasForciblyMerged()
	{
		return myWasForciblyMerged;
	}
}