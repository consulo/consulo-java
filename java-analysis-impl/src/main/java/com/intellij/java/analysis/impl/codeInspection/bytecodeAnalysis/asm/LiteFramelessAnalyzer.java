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
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm;

import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;

import java.util.List;

/**
 * Specialized lite version of {@link FramelessAnalyzer}.
 * No processing of Subroutines. May be used for methods without JSR/RET instructions.
 *
 * @author lambdamix
 */
public class LiteFramelessAnalyzer extends FramelessAnalyzer
{
	public LiteFramelessAnalyzer(EdgeCreator creator)
	{
		super(creator);
	}

	@Override
	protected void findSubroutine(int insn, Subroutine sub, List<AbstractInsnNode> calls)
	{
	}

	@Override
	protected void merge(final int insn, final Subroutine subroutine)
	{
		if(!wasQueued[insn])
		{
			wasQueued[insn] = true;
			if(!queued[insn])
			{
				queued[insn] = true;
				queue[top++] = insn;
			}
		}
	}

	@Override
	protected void merge(int insn, Subroutine subroutineBeforeJSR, boolean[] access)
	{
		if(!wasQueued[insn])
		{
			wasQueued[insn] = true;
			if(!queued[insn])
			{
				queued[insn] = true;
				queue[top++] = insn;
			}
		}
	}
}