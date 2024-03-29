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

import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm.ControlFlowGraph.Edge;
import consulo.internal.org.objectweb.asm.tree.MethodNode;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.util.collection.primitive.ints.IntIntMap;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.util.collection.primitive.ints.IntMaps;

import java.util.HashSet;
import java.util.Set;

import static consulo.internal.org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static consulo.internal.org.objectweb.asm.Opcodes.ACC_NATIVE;

final class ControlFlowBuilder implements FramelessAnalyzer.EdgeCreator
{
	final String className;
	final MethodNode methodNode;
	final IntList[] transitions;
	final Set<ControlFlowGraph.Edge> errorTransitions;
	final IntIntMap npeTransitions;
	final FramelessAnalyzer myAnalyzer;
	private final boolean[] errors;
	private int edgeCount;

	ControlFlowBuilder(String className, MethodNode methodNode, boolean jsr)
	{
		myAnalyzer = jsr ? new FramelessAnalyzer(this) : new LiteFramelessAnalyzer(this);
		this.className = className;
		this.methodNode = methodNode;
		transitions = new IntList[methodNode.instructions.size()];
		errors = new boolean[methodNode.instructions.size()];
		for(int i = 0; i < transitions.length; i++)
		{
			transitions[i] = IntLists.newArrayList();
		}
		errorTransitions = new HashSet<>();
		npeTransitions = IntMaps.newIntIntHashMap();
	}

	final ControlFlowGraph buildCFG() throws AnalyzerException
	{
		if((methodNode.access & (ACC_ABSTRACT | ACC_NATIVE)) == 0)
		{
			myAnalyzer.analyze(methodNode);
		}
		int[][] resultTransitions = new int[transitions.length][];
		for(int i = 0; i < resultTransitions.length; i++)
		{
			resultTransitions[i] = transitions[i].toArray();
		}
		return new ControlFlowGraph(className, methodNode, resultTransitions, edgeCount, errors, errorTransitions, npeTransitions);
	}

	@Override
	public final void newControlFlowEdge(int insn, int successor)
	{
		if(!transitions[insn].contains(successor))
		{
			transitions[insn].add(successor);
			edgeCount++;
		}
	}

	@Override
	public final void newControlFlowExceptionEdge(int insn, int successor, boolean npe)
	{
		if(!transitions[insn].contains(successor))
		{
			transitions[insn].add(successor);
			edgeCount++;
			Edge edge = new Edge(insn, successor);
			errorTransitions.add(edge);
			if(npe && !npeTransitions.containsKey(insn))
			{
				npeTransitions.putInt(insn, successor);
			}
			errors[successor] = true;
		}
	}
}