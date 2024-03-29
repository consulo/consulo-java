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
package com.intellij.java.debugger.impl.engine;

import com.intellij.java.debugger.SourcePosition;
import com.intellij.java.debugger.engine.evaluation.EvaluateException;
import com.intellij.java.debugger.impl.jdi.VirtualMachineProxyImpl;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiLambdaExpression;
import com.intellij.java.language.psi.PsiStatement;
import consulo.internal.com.sun.jdi.Location;
import consulo.internal.com.sun.jdi.Method;
import consulo.language.psi.PsiElement;
import consulo.util.lang.Range;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/26/13
 */
public class LambdaMethodFilter implements BreakpointStepMethodFilter
{
	public static final String LAMBDA_METHOD_PREFIX = "lambda$";
	private final int myLambdaOrdinal;
	@Nullable
	private final SourcePosition myFirstStatementPosition;
	private final int myLastStatementLine;
	private final Range<Integer> myCallingExpressionLines;

	public LambdaMethodFilter(PsiLambdaExpression lambda, int expressionOrdinal, Range<Integer> callingExpressionLines)
	{
		myLambdaOrdinal = expressionOrdinal;
		myCallingExpressionLines = callingExpressionLines;

		SourcePosition firstStatementPosition = null;
		SourcePosition lastStatementPosition = null;
		final PsiElement body = lambda.getBody();
		if(body instanceof PsiCodeBlock)
		{
			final PsiStatement[] statements = ((PsiCodeBlock) body).getStatements();
			if(statements.length > 0)
			{
				firstStatementPosition = SourcePosition.createFromElement(statements[0]);
				if(firstStatementPosition != null)
				{
					final PsiStatement lastStatement = statements[statements.length - 1];
					lastStatementPosition = SourcePosition.createFromOffset(firstStatementPosition.getFile(),
							lastStatement.getTextRange().getEndOffset());
				}
			}
		}
		else if(body != null)
		{
			firstStatementPosition = SourcePosition.createFromElement(body);
		}
		myFirstStatementPosition = firstStatementPosition;
		myLastStatementLine = lastStatementPosition != null ? lastStatementPosition.getLine() : -1;
	}

	public int getLambdaOrdinal()
	{
		return myLambdaOrdinal;
	}

	@Override
	@Nullable
	public SourcePosition getBreakpointPosition()
	{
		return myFirstStatementPosition;
	}

	@Override
	public int getLastStatementLine()
	{
		return myLastStatementLine;
	}

	@Override
	public boolean locationMatches(DebugProcessImpl process, Location location) throws EvaluateException
	{
		final VirtualMachineProxyImpl vm = process.getVirtualMachineProxy();
		final Method method = location.method();
		return isLambdaName(method.name()) && (!vm.canGetSyntheticAttribute() || method.isSynthetic());
	}

	@Nullable
	@Override
	public Range<Integer> getCallingExpressionLines()
	{
		return myCallingExpressionLines;
	}

	public static boolean isLambdaName(@Nullable String name)
	{
		return !StringUtil.isEmpty(name) && name.startsWith(LAMBDA_METHOD_PREFIX);
	}

	public static int getLambdaOrdinal(@Nonnull String name)
	{
		int pos = name.lastIndexOf('$');
		if(pos > -1)
		{
			try
			{
				return Integer.parseInt(name.substring(pos + 1));
			}
			catch(NumberFormatException ignored)
			{
			}
		}
		return -1;
	}
}
