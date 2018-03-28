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

package com.intellij.codeInspection.dataFlow;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;

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
		MethodContract.ValueConstraint ret = c.getReturnValue();
		return ret == MethodContract.ValueConstraint.TRUE_VALUE || ret == MethodContract.ValueConstraint.FALSE_VALUE ? new StandardMethodContract(c.arguments, ret.negate()) : null;
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

		if(negated != null ? !negated.equals(that.negated) : that.negated != null)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		return negated != null ? negated.hashCode() : 0;
	}
}
