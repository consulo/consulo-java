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

import com.intellij.java.language.codeInsight.NullableNotNullManager;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * from kotlin
 */
class MethodCallContract implements PreContract
{
	private final ExpressionRange call;
	private final List<List<StandardMethodContract.ValueConstraint>> states;

	MethodCallContract(ExpressionRange call, List<List<StandardMethodContract.ValueConstraint>> states)
	{
		this.call = call;
		this.states = states;
	}

	public ExpressionRange getCall()
	{
		return call;
	}

	public List<List<StandardMethodContract.ValueConstraint>> getStates()
	{
		return states;
	}

	@Nonnull
	@Override
	public List<StandardMethodContract> toContracts(PsiMethod method, Supplier<PsiCodeBlock> body)
	{
		PsiExpression expression = call.restoreExpression(body.get());
		if(!(expression instanceof PsiMethodCallExpression))
		{
			return Collections.emptyList();
		}
		PsiMethod target = ((PsiMethodCallExpression) expression).resolveMethod();
		if(target != null && NullableNotNullManager.isNotNull(target))
		{
			return ContractInferenceInterpreter.toContracts(ContainerUtil.map(states, it -> it.toArray(new StandardMethodContract.ValueConstraint[it.size()])), ContractReturnValue.returnNotNull());
		}
		return Collections.emptyList();
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
		MethodCallContract that = (MethodCallContract) o;
		return Objects.equals(call, that.call) &&
				Objects.equals(states, that.states);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(call, states);
	}
}
