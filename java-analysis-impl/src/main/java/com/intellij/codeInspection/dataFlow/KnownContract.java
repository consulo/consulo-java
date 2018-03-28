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

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiMethod;

/**
 * from kotlin
 */
class KnownContract implements PreContract
{
	private final StandardMethodContract myContract;

	KnownContract(StandardMethodContract contract)
	{
		this.myContract = contract;
	}

	public StandardMethodContract getContract()
	{
		return myContract;
	}

	@Nonnull
	@Override
	public List<StandardMethodContract> toContracts(PsiMethod method, Supplier<PsiCodeBlock> body)
	{
		return Collections.singletonList(myContract);
	}

	@javax.annotation.Nullable
	@Override
	public PreContract negate()
	{
		StandardMethodContract negateContract = NegatingContract.negateContract(myContract);
		return negateContract != null ? new KnownContract(negateContract) : null;
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

		KnownContract that = (KnownContract) o;

		if(myContract != null ? !myContract.equals(that.myContract) : that.myContract != null)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		return myContract != null ? myContract.hashCode() : 0;
	}
}
