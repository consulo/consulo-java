// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm;

import consulo.internal.org.objectweb.asm.tree.JumpInsnNode;
import consulo.internal.org.objectweb.asm.tree.LabelNode;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author lambdamix
 */
public class Subroutine
{
	LabelNode start;
	boolean[] access;
	List<JumpInsnNode> callers;

	private Subroutine()
	{
	}

	Subroutine(@Nullable LabelNode start, int maxLocals, @Nullable JumpInsnNode caller)
	{
		this.start = start;
		this.access = new boolean[maxLocals];
		this.callers = new ArrayList<>();
		callers.add(caller);
	}

	public Subroutine copy()
	{
		Subroutine result = new Subroutine();
		result.start = start;
		result.access = new boolean[access.length];
		System.arraycopy(access, 0, result.access, 0, access.length);
		result.callers = new ArrayList<>(callers);
		return result;
	}

	public boolean merge(Subroutine subroutine)
	{
		boolean changes = false;
		for(int i = 0; i < access.length; ++i)
		{
			if(subroutine.access[i] && !access[i])
			{
				access[i] = true;
				changes = true;
			}
		}
		if(subroutine.start == start)
		{
			for(int i = 0; i < subroutine.callers.size(); ++i)
			{
				JumpInsnNode caller = subroutine.callers.get(i);
				if(!callers.contains(caller))
				{
					callers.add(caller);
					changes = true;
				}
			}
		}
		return changes;
	}
}