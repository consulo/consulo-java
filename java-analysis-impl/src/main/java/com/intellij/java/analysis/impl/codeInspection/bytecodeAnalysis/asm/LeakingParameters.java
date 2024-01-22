// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm;

import consulo.internal.org.objectweb.asm.tree.AbstractInsnNode;
import consulo.internal.org.objectweb.asm.tree.InsnList;
import consulo.internal.org.objectweb.asm.tree.MethodNode;
import consulo.internal.org.objectweb.asm.tree.analysis.Analyzer;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.Frame;
import consulo.internal.org.objectweb.asm.tree.analysis.Value;
import jakarta.annotation.Nonnull;

/**
 * @author lambdamix
 */
public class LeakingParameters
{
	public final Frame<? extends Value>[] frames;
	public final boolean[] parameters;
	public final boolean[] nullableParameters;

	public LeakingParameters(Frame<? extends Value>[] frames, boolean[] parameters, boolean[] nullableParameters)
	{
		this.frames = frames;
		this.parameters = parameters;
		this.nullableParameters = nullableParameters;
	}

	@Nonnull
	public static LeakingParameters build(String className, MethodNode methodNode, boolean jsr) throws AnalyzerException
	{
		Frame<ParamsValue>[] frames = jsr ? new Analyzer<>(new ParametersUsage(methodNode)).analyze(className, methodNode)
				: new LiteAnalyzer<>(new ParametersUsage(methodNode)).analyze(className, methodNode);
		InsnList insns = methodNode.instructions;
		LeakingParametersCollector collector = new LeakingParametersCollector(methodNode);
		for(int i = 0; i < frames.length; i++)
		{
			AbstractInsnNode insnNode = insns.get(i);
			Frame<ParamsValue> frame = frames[i];
			if(frame != null)
			{
				switch(insnNode.getType())
				{
					case AbstractInsnNode.LABEL:
					case AbstractInsnNode.LINE:
					case AbstractInsnNode.FRAME:
						break;
					default:
						new Frame<>(frame).execute(insnNode, collector);
				}
			}
		}
		boolean[] notNullParameters = collector.leaking;
		boolean[] nullableParameters = collector.nullableLeaking;
		for(int i = 0; i < nullableParameters.length; i++)
		{
			nullableParameters[i] |= notNullParameters[i];
		}
		return new LeakingParameters(frames, notNullParameters, nullableParameters);
	}

	@jakarta.annotation.Nonnull
	public static LeakingParameters buildFast(String className, MethodNode methodNode, boolean jsr) throws AnalyzerException
	{
		IParametersUsage parametersUsage = new IParametersUsage(methodNode);
		Frame<?>[] frames = jsr ? new Analyzer<>(parametersUsage).analyze(className, methodNode)
				: new LiteAnalyzer<>(parametersUsage).analyze(className, methodNode);
		int leakingMask = parametersUsage.leaking;
		int nullableLeakingMask = parametersUsage.nullableLeaking;
		boolean[] notNullParameters = new boolean[parametersUsage.arity];
		boolean[] nullableParameters = new boolean[parametersUsage.arity];
		for(int i = 0; i < notNullParameters.length; i++)
		{
			notNullParameters[i] = (leakingMask & (1 << i)) != 0;
			nullableParameters[i] = ((leakingMask | nullableLeakingMask) & (1 << i)) != 0;
		}
		return new LeakingParameters(frames, notNullParameters, nullableParameters);
	}
}