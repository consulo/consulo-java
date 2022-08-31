/*
 * Copyright 2013-2017 consulo.io
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

import com.intellij.java.analysis.impl.codeInspection.dataFlow.ContractReturnValue;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.java.language.psi.PsiCodeBlock;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * from kotlin
 */
class NegatingContract implements PreContract
{
	private final PreContract negated;

	NegatingContract(PreContract negated)
	{
		this.negated = negated;
	}

	public PreContract getNegated()
	{
		return negated;
	}

	@Nonnull
	@Override
	public List<StandardMethodContract> toContracts(PsiMethod method, Supplier<PsiCodeBlock> body)
	{
		return ContainerUtil.mapNotNull(negated.toContracts(method, body), NegatingContract::negateContract);
	}

	static StandardMethodContract negateContract(StandardMethodContract c)
	{
		ContractReturnValue returnValue = c.getReturnValue();
		if(returnValue instanceof ContractReturnValue.BooleanReturnValue)
		{
			return c.withReturnValue(((ContractReturnValue.BooleanReturnValue) returnValue).negate());
		}
		else
		{
			return null;
		}
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
		NegatingContract that = (NegatingContract) o;
		return Objects.equals(negated, that.negated);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(negated);
	}
}
