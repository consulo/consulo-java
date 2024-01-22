// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.controlFlow;

import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Map;

abstract class AbstractControlFlow implements ControlFlow
{
	// Low 4 bytes = start; high 4 bytes = end
	final
	@jakarta.annotation.Nonnull
	Map<PsiElement, int[]> myElementToOffsetMap;

	AbstractControlFlow(@jakarta.annotation.Nonnull Map<PsiElement, int[]> map)
	{
		myElementToOffsetMap = map;
	}

	@Override
	public int getStartOffset(@Nonnull PsiElement element)
	{
		int[] offsets = myElementToOffsetMap.get(element);
		return offsets == null ? -1 : offsets[0];
	}

	@Override
	public int getEndOffset(@Nonnull PsiElement element)
	{
		int[] offsets = myElementToOffsetMap.get(element);
		return offsets == null ? -1 : offsets[1];
	}

	public String toString()
	{
		StringBuilder buffer = new StringBuilder();
		List<Instruction> instructions = getInstructions();
		for(int i = 0; i < instructions.size(); i++)
		{
			Instruction instruction = instructions.get(i);
			buffer.append(i).append(": ").append(instruction).append("\n");
		}
		return buffer.toString();
	}
}
