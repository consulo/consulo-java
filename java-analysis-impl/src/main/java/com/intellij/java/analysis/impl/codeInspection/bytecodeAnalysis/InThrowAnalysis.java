package com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis;

import com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.asm.RichControlFlow;
import consulo.internal.org.objectweb.asm.tree.analysis.BasicValue;
import consulo.internal.org.objectweb.asm.tree.analysis.Frame;

import static com.intellij.java.analysis.impl.codeInspection.bytecodeAnalysis.AbstractValues.*;
import static consulo.internal.org.objectweb.asm.Opcodes.*;

class InThrowAnalysis extends ContractAnalysis
{
	private BasicValue myReturnValue;
	boolean myHasNonTrivialReturn;

	protected InThrowAnalysis(RichControlFlow richControlFlow,
							  Direction direction,
							  boolean[] resultOrigins,
							  boolean stable,
							  State[] pending)
	{
		super(richControlFlow, direction, resultOrigins, stable, pending);
	}

	@Override
	boolean handleReturn(Frame<BasicValue> frame, int opcode, boolean unsure)
	{
		Result subResult;
		if(interpreter.deReferenced)
		{
			subResult = Value.Top;
		}
		else
		{
			switch(opcode)
			{
				case ARETURN:
				case IRETURN:
				case LRETURN:
				case FRETURN:
				case DRETURN:
					BasicValue value = frame.pop();
					if(!(value instanceof NthParamValue) && value != NullValue && value != TrueValue && value != FalseValue ||
							myReturnValue != null && !myReturnValue.equals(value))
					{
						myHasNonTrivialReturn = true;
					}
					else
					{
						myReturnValue = value;
					}
					subResult = Value.Top;
					break;
				case RETURN:
					subResult = Value.Top;
					break;
				case ATHROW:
					subResult = Value.Fail;
					break;
				default:
					return false;
			}
		}
		internalResult = resultUtil.join(internalResult, subResult);
		unsureOnly &= unsure;
		if(!unsure && internalResult == Value.Top && myHasNonTrivialReturn)
		{
			earlyResult = internalResult;
		}
		return true;
	}
}