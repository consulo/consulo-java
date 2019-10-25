/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.asm.AnalyzerExt;
import com.intellij.codeInspection.bytecodeAnalysis.asm.LiteAnalyzerExt;
import consulo.internal.org.objectweb.asm.Opcodes;
import consulo.internal.org.objectweb.asm.tree.InsnList;
import consulo.internal.org.objectweb.asm.tree.MethodNode;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;
import consulo.internal.org.objectweb.asm.tree.analysis.Frame;
import javax.annotation.Nonnull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class NullableMethodAnalysis
{
	private static final BasicValue lNull = new LabeledNull(0);

	static Result analyze(MethodNode methodNode, boolean[] origins, boolean jsr) throws AnalyzerException
	{
		InsnList insns = methodNode.instructions;
		Constraint[] data = new Constraint[insns.size()];
		int[] originsMapping = mapOrigins(origins);

		NullableMethodInterpreter interpreter = new NullableMethodInterpreter(insns, origins, originsMapping);
		Frame<BasicValue>[] frames =
				jsr ?
						new AnalyzerExt<>(interpreter, data, Constraint.EMPTY).analyze("this", methodNode) :
						new LiteAnalyzerExt<>(interpreter, data, Constraint.EMPTY).analyze("this", methodNode);

		BasicValue result = BasicValue.REFERENCE_VALUE;
		for(int i = 0; i < frames.length; i++)
		{
			Frame<BasicValue> frame = frames[i];
			if(frame != null && insns.get(i).getOpcode() == Opcodes.ARETURN)
			{
				BasicValue stackTop = frame.pop();
				result = combine(result, stackTop, data[i]);
			}
		}
		if(result instanceof LabeledNull)
		{
			return Value.Null;
		}
		if(result instanceof Calls)
		{
			Calls calls = ((Calls) result);
			int mergedMappedLabels = calls.mergedLabels;
			if(mergedMappedLabels != 0)
			{
				Set<Component> sum = new HashSet<>();
				EKey[] createdKeys = interpreter.keys;
				for(int origin = 0; origin < originsMapping.length; origin++)
				{
					int mappedOrigin = originsMapping[origin];
					EKey createdKey = createdKeys[origin];
					if(createdKey != null && (mergedMappedLabels & (1 << mappedOrigin)) != 0)
					{
						sum.add(new Component(Value.Null, Collections.singleton(createdKey)));
					}
				}
				if(!sum.isEmpty())
				{
					return new Pending(sum);
				}
			}
		}
		return Value.Bot;
	}

	@Nonnull
	private static int[] mapOrigins(boolean[] origins)
	{
		int[] originsMapping = new int[origins.length];
		int mapped = 0;
		for(int i = 0; i < origins.length; i++)
		{
			originsMapping[i] = origins[i] ? mapped++ : -1;
		}
		return originsMapping;
	}

	static BasicValue combine(BasicValue v1, BasicValue v2, Constraint constraint)
	{
		if(v1 instanceof LabeledNull)
		{
			return lNull;
		}
		else if(v2 instanceof LabeledNull)
		{
			int v2Origins = ((LabeledNull) v2).origins;
			int constraintOrigins = constraint.nulls;
			int intersect = v2Origins & constraintOrigins;
			return intersect == v2Origins ? v1 : lNull;
		}
		else if(v1 instanceof Calls)
		{
			if(v2 instanceof Calls)
			{
				Calls calls1 = (Calls) v1;
				Calls calls2 = (Calls) v2;
				int labels2 = calls2.mergedLabels;
				int aliveLabels2 = labels2 - (labels2 & constraint.calls);
				return new Calls(calls1.mergedLabels | aliveLabels2);
			}
			else
			{
				return v1;
			}
		}
		else if(v2 instanceof Calls)
		{
			Calls calls2 = (Calls) v2;
			int labels2 = calls2.mergedLabels;
			int aliveLabels2 = labels2 - (labels2 & constraint.calls);
			return new Calls(aliveLabels2);
		}
		return BasicValue.REFERENCE_VALUE;
	}
}

