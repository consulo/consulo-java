// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.controlFlow;

import consulo.logging.Logger;
import consulo.language.psi.PsiElement;
import consulo.util.collection.Stack;
import jakarta.annotation.Nonnull;

import java.util.*;

class ControlFlowImpl extends AbstractControlFlow
{
	private static final Logger LOG = Logger.getInstance(ControlFlowImpl.class);

	private final List<Instruction> myInstructions = new ArrayList<>();
	private final List<PsiElement> myElementsForInstructions = new ArrayList<>();
	private boolean myConstantConditionOccurred;
	private final Map<Instruction, Instruction> myInstructionCache = new HashMap<>();
	private final Stack<PsiElement> myElementStack = new Stack<>();

	protected ControlFlowImpl()
	{
		super(new HashMap<>());
	}

	void addInstruction(Instruction instruction)
	{
		if(instruction instanceof ReadVariableInstruction || instruction instanceof WriteVariableInstruction)
		{
			Instruction oldInstruction = myInstructionCache.putIfAbsent(instruction, instruction);
			if(oldInstruction != null)
			{
				instruction = oldInstruction;
			}
		}
		myInstructions.add(instruction);
		myElementsForInstructions.add(myElementStack.peek());
	}

	public void startElement(PsiElement element)
	{
		myElementStack.push(element);
		myElementToOffsetMap.put(element, new int[]{myInstructions.size(), -1});
		assert getStartOffset(element) == myInstructions.size();
	}

	void finishElement(PsiElement element)
	{
		PsiElement popped = myElementStack.pop();
		LOG.assertTrue(popped.equals(element));
		myElementToOffsetMap.get(element)[1] = myInstructions.size();
		assert getEndOffset(element) == myInstructions.size();
	}

	@Override
	@Nonnull
	public List<Instruction> getInstructions()
	{
		return myInstructions;
	}

	@Override
	public int getSize()
	{
		return myInstructions.size();
	}

	ControlFlow immutableCopy()
	{
		return new ImmutableControlFlow(myInstructions.toArray(new Instruction[0]),
				new HashMap<>(myElementToOffsetMap),
				myElementsForInstructions.toArray(PsiElement.EMPTY_ARRAY),
				myConstantConditionOccurred);
	}

	@Override
	public PsiElement getElement(int offset)
	{
		return myElementsForInstructions.get(offset);
	}

	@Override
	public boolean isConstantConditionOccurred()
	{
		return myConstantConditionOccurred;
	}

	void setConstantConditionOccurred(boolean constantConditionOccurred)
	{
		myConstantConditionOccurred = constantConditionOccurred;
	}

	private static final class ImmutableControlFlow extends AbstractControlFlow
	{
		private final
		@jakarta.annotation.Nonnull
		List<Instruction> myInstructions;
		private final
		@jakarta.annotation.Nonnull
		PsiElement[] myElementsForInstructions;
		private final boolean myConstantConditionOccurred;

		private ImmutableControlFlow(@Nonnull Instruction[] instructions,
                                 @Nonnull HashMap<PsiElement, int[]> myElementToOffsetMap,
                                 @jakarta.annotation.Nonnull PsiElement [] elementsForInstructions, boolean occurred)
		{
			super(myElementToOffsetMap);
			myInstructions = Arrays.asList(instructions);
			myElementsForInstructions = elementsForInstructions;
			myConstantConditionOccurred = occurred;
		}

		@Override
		@jakarta.annotation.Nonnull
		public List<Instruction> getInstructions()
		{
			return myInstructions;
		}

		@Override
		public int getSize()
		{
			return myInstructions.size();
		}

		@Override
		public PsiElement getElement(int offset)
		{
			return myElementsForInstructions[offset];
		}

		@Override
		public boolean isConstantConditionOccurred()
		{
			return myConstantConditionOccurred;
		}
	}
}