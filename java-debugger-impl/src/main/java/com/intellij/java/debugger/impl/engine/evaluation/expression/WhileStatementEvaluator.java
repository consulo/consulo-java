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
package com.intellij.java.debugger.impl.engine.evaluation.expression;

import jakarta.annotation.Nonnull;

import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.engine.evaluation.EvaluateExceptionUtil;
import com.intellij.java.debugger.impl.engine.evaluation.EvaluationContextImpl;
import com.intellij.java.debugger.engine.evaluation.expression.Modifier;
import consulo.internal.com.sun.jdi.BooleanValue;

/**
 * @author lex
 */
public class WhileStatementEvaluator extends LoopEvaluator
{
	private final Evaluator myConditionEvaluator;

	public WhileStatementEvaluator(@Nonnull Evaluator conditionEvaluator, Evaluator bodyEvaluator, String labelName)
	{
		super(labelName, bodyEvaluator);
		myConditionEvaluator = DisableGC.create(conditionEvaluator);
	}

	@Override
	public Modifier getModifier()
	{
		return myConditionEvaluator.getModifier();
	}

	@Override
	public Object evaluate(EvaluationContextImpl context) throws EvaluateException
	{
		Object value;
		while(true)
		{
			value = myConditionEvaluator.evaluate(context);
			if(!(value instanceof BooleanValue))
			{
				throw EvaluateExceptionUtil.BOOLEAN_EXPECTED;
			}
			else
			{
				if(!((BooleanValue) value).booleanValue())
				{
					break;
				}
			}

			if(body(context))
			{
				break;
			}
		}

		return value;
	}
}
