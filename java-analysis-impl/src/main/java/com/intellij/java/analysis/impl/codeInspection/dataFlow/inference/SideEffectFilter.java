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

package com.intellij.java.analysis.impl.codeInspection.dataFlow.inference;

import com.intellij.java.analysis.impl.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.siyeh.ig.psiutils.SideEffectChecker;
import consulo.annotation.access.RequiredReadAction;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * from kotlin
 */
class SideEffectFilter implements PreContract
{
	private List<ExpressionRange> expressionsToCheck;
	private List<PreContract> contracts;

	SideEffectFilter(List<ExpressionRange> expressionsToCheck, List<PreContract> contracts)
	{
		this.expressionsToCheck = expressionsToCheck;
		this.contracts = contracts;
	}

	public List<ExpressionRange> getExpressionsToCheck()
	{
		return expressionsToCheck;
	}

	public List<PreContract> getContracts()
	{
		return contracts;
	}

	@Nonnull
	@Override
	@RequiredReadAction
	public List<StandardMethodContract> toContracts(PsiMethod method, Supplier<PsiCodeBlock> body)
	{
		for(ExpressionRange expressionRange : expressionsToCheck)
		{
			if(mayHaveSideEffects(body.get(), expressionRange))
			{
				return Collections.emptyList();
			}
		}
		return contracts.stream().flatMap(it -> it.toContracts(method, body).stream()).collect(Collectors.toList());
	}

	@RequiredReadAction
	private static boolean mayHaveSideEffects(PsiCodeBlock body, ExpressionRange range)
	{
		PsiExpression expr = range.restoreExpression(body);
		return expr != null && SideEffectChecker.mayHaveSideEffects(expr);
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}

		SideEffectFilter that = (SideEffectFilter) o;

		if(expressionsToCheck != null ? !expressionsToCheck.equals(that.expressionsToCheck) : that.expressionsToCheck != null)
		{
			return false;
		}
		if(contracts != null ? !contracts.equals(that.contracts) : that.contracts != null)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = expressionsToCheck != null ? expressionsToCheck.hashCode() : 0;
		result = 31 * result + (contracts != null ? contracts.hashCode() : 0);
		return result;
	}
}
