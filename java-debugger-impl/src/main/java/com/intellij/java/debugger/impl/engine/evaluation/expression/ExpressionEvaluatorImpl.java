/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.debugger.impl.engine.evaluation.expression;

import com.intellij.java.debugger.DebuggerBundle;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.engine.evaluation.EvaluationContext;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import consulo.internal.com.sun.jdi.Value;
import consulo.logging.Logger;

public class ExpressionEvaluatorImpl implements ExpressionEvaluator
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator");
	private final Evaluator myEvaluator;

	public ExpressionEvaluatorImpl(Evaluator evaluator)
	{
		myEvaluator = evaluator;
	}

	//call evaluate before
	@Override
	public Modifier getModifier()
	{
		return myEvaluator.getModifier();
	}

	// EvaluationContextImpl should be at the same stackFrame as it was in the call to EvaluatorBuilderImpl.build
	@Override
	public Value evaluate(final EvaluationContext context) throws EvaluateException
	{
		if(!context.getDebugProcess().isAttached())
		{
			throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("error.vm.disconnected"));
		}
		try
		{
			if(context.getFrameProxy() == null)
			{
				throw EvaluateExceptionUtil.NULL_STACK_FRAME;
			}

			Object value = myEvaluator.evaluate((EvaluationContextImpl) context);

			if(value != null && !(value instanceof Value))
			{
				throw EvaluateExceptionUtil.createEvaluateException(DebuggerBundle.message("evaluation.error.invalid.expression", ""));
			}

			return (Value) value;
		}
		catch(ReturnEvaluator.ReturnException r)
		{
			return (Value) r.getReturnValue();
		}
		catch(Throwable/*IncompatibleThreadStateException*/ e)
		{
			LOG.debug(e);
			if(e instanceof EvaluateException)
			{
				throw ((EvaluateException) e);
			}
			else
			{
				throw EvaluateExceptionUtil.createEvaluateException(e);
			}
		}
	}
}
