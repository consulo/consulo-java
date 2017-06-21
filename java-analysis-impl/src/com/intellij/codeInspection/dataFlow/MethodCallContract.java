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

package com.intellij.codeInspection.dataFlow;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.util.containers.ContainerUtil;

/**
 * from kotlin
 */
class MethodCallContract implements PreContract
{
	private final ExpressionRange call;
	private final List<List<MethodContract.ValueConstraint>> states;

	MethodCallContract(ExpressionRange call, List<List<MethodContract.ValueConstraint>> states)
	{
		this.call = call;
		this.states = states;
	}

	public ExpressionRange getCall()
	{
		return call;
	}

	public List<List<MethodContract.ValueConstraint>> getStates()
	{
		return states;
	}

	@NotNull
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
			return ContractInferenceInterpreter.toContracts(ContainerUtil.map(states, it -> it.toArray(new MethodContract.ValueConstraint[it.size()])), MethodContract.ValueConstraint.NOT_NULL_VALUE);
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

		if(call != null ? !call.equals(that.call) : that.call != null)
		{
			return false;
		}
		if(states != null ? !states.equals(that.states) : that.states != null)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		return call.hashCode() * 31 + ContainerUtil.flatten(states).stream().map(Enum::ordinal).collect(Collectors.toList()).hashCode();
	}
}
