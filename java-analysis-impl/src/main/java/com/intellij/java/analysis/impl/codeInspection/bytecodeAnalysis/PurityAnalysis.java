/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.tree.MethodNode;
import consulo.internal.org.objectweb.asm.tree.analysis.Analyzer;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Produces equations for inference of @Contract(pure=true) annotations.
 * Scala source at https://github.com/ilya-klyuchnikov/faba
 * Algorithm: https://github.com/ilya-klyuchnikov/faba/blob/ef1c15b4758517652e939f67099bbec0260e9e68/notes/purity.md
 */
public class PurityAnalysis
{
	static final int UN_ANALYZABLE_FLAG = Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_INTERFACE;

	/**
	 * @param method     a method descriptor
	 * @param methodNode an ASM MethodNode
	 * @param stable     whether a method is stable (e.g. final or declared in final class)
	 * @return a purity equation or null for top result (either impure or unknown, impurity assumed)
	 */
	@Nullable
	public static Equation analyze(Member method, MethodNode methodNode, boolean stable)
	{
		EKey key = new EKey(method, Direction.Pure, stable);
		Effects hardCodedSolution = HardCodedPurity.getInstance().getHardCodedSolution(method);
		if(hardCodedSolution != null)
		{
			return new Equation(key, hardCodedSolution);
		}

		if((methodNode.access & UN_ANALYZABLE_FLAG) != 0)
			return null;

		DataInterpreter dataInterpreter = new DataInterpreter(methodNode);
		try
		{
			new Analyzer<>(dataInterpreter).analyze("this", methodNode);
		}
		catch(AnalyzerException e)
		{
			return null;
		}
		EffectQuantum[] quanta = dataInterpreter.effects;
		DataValue returnValue = dataInterpreter.returnValue == null ? DataValue.UnknownDataValue1 : dataInterpreter.returnValue;
		Set<EffectQuantum> effects = new HashSet<>();
		for(EffectQuantum effectQuantum : quanta)
		{
			if(effectQuantum != null)
			{
				if(effectQuantum == EffectQuantum.TopEffectQuantum)
				{
					return returnValue == DataValue.UnknownDataValue1 ? null : new Equation(key, new Effects(returnValue, Effects.TOP_EFFECTS));
				}
				effects.add(effectQuantum);
			}
		}
		return new Equation(key, new Effects(returnValue, effects));
	}
}


