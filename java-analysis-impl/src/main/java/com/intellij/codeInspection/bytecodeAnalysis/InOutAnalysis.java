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

package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.codeInspection.bytecodeAnalysis.asm.RichControlFlow;
import consulo.internal.org.objectweb.asm.tree.analysis.AnalyzerException;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;
import consulo.internal.org.objectweb.asm.tree.analysis.Frame;

import java.util.Set;

import static com.intellij.codeInspection.bytecodeAnalysis.AbstractValues.*;
import static consulo.internal.org.objectweb.asm.Opcodes.*;

class InOutAnalysis extends ContractAnalysis
{

	protected InOutAnalysis(RichControlFlow richControlFlow,
							Direction direction,
							boolean[] resultOrigins,
							boolean stable,
							State[] pending)
	{
		super(richControlFlow, direction, resultOrigins, stable, pending);
	}

	@Override
	boolean handleReturn(Frame<BasicValue> frame, int opcode, boolean unsure) throws AnalyzerException
	{
		if(interpreter.deReferenced)
		{
			return true;
		}
		switch(opcode)
		{
			case ARETURN:
			case IRETURN:
			case LRETURN:
			case FRETURN:
			case DRETURN:
			case RETURN:
				BasicValue stackTop = popValue(frame);
				Result subResult;
				if(FalseValue == stackTop)
				{
					subResult = Value.False;
				}
				else if(TrueValue == stackTop)
				{
					subResult = Value.True;
				}
				else if(NullValue == stackTop)
				{
					subResult = Value.Null;
				}
				else if(stackTop instanceof NotNullValue)
				{
					subResult = Value.NotNull;
				}
				else if(stackTop instanceof ParamValue)
				{
					subResult = inValue;
				}
				else if(stackTop instanceof CallResultValue)
				{
					Set<EKey> keys = ((CallResultValue) stackTop).inters;
					subResult = new Pending(new Component[]{new Component(Value.Top, keys)});
				}
				else
				{
					earlyResult = Value.Top;
					return true;
				}
				internalResult = checkLimit(resultUtil.join(internalResult, subResult));
				unsureOnly &= unsure;
				if(!unsure && internalResult == Value.Top)
				{
					earlyResult = internalResult;
				}
				return true;
			case ATHROW:
				return true;
			default:
		}
		return false;
	}
}
