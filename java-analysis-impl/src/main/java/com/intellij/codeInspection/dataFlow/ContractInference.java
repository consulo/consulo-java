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

import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.ANY_VALUE;
import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.FALSE_VALUE;
import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.NOT_NULL_VALUE;
import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.NULL_VALUE;
import static com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint.TRUE_VALUE;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;

/**
 * @author peter
 */
public class ContractInference
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.ContractInferenceInterpreter");
	public static final int MAX_CONTRACT_COUNT = 10;

	@Nonnull
	public static List<StandardMethodContract> inferContracts(@Nonnull PsiMethodImpl method)
	{
		if(!InferenceFromSourceUtil.shouldInferFromSource(method))
		{
			return Collections.emptyList();
		}

		return CachedValuesManager.getCachedValue(method, () ->
		{
			MethodData data = ContractInferenceIndexKt.getIndexedData(method);
			List<PreContract> preContracts = data == null ? Collections.emptyList() : data.getContracts();
			List<StandardMethodContract> result = RecursionManager.doPreventingRecursion(method, true, () -> postProcessContracts(method, data, preContracts));
			if(result == null)
			{
				result = Collections.emptyList();
			}
			return CachedValueProvider.Result.create(result, method, PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
		});
	}

	@Nonnull
	private static List<StandardMethodContract> postProcessContracts(@Nonnull PsiMethodImpl method, MethodData data, List<PreContract> rawContracts)
	{
		List<StandardMethodContract> contracts = ContainerUtil.concat(rawContracts, c -> c.toContracts(method, data.methodBody(method)));
		if(contracts.isEmpty())
		{
			return Collections.emptyList();
		}

		final PsiType returnType = method.getReturnType();
		if(returnType != null && !(returnType instanceof PsiPrimitiveType))
		{
			contracts = boxReturnValues(contracts);
		}
		List<StandardMethodContract> compatible = ContainerUtil.filter(contracts, contract -> isContractCompatibleWithMethod(method, returnType, contract));
		if(compatible.size() > MAX_CONTRACT_COUNT)
		{
			LOG.debug("Too many contracts for " + PsiUtil.getMemberQualifiedName(method) + ", shrinking the list");
			return compatible.subList(0, MAX_CONTRACT_COUNT);
		}
		return compatible;
	}

	private static boolean isContractCompatibleWithMethod(@Nonnull PsiMethod method, PsiType returnType, StandardMethodContract contract)
	{
		if(hasContradictoryExplicitParameterNullity(method, contract))
		{
			return false;
		}
		if(isReturnNullitySpecifiedExplicitly(method, contract))
		{
			return false;
		}
		if(isContradictingExplicitNullableReturn(method, contract))
		{
			return false;
		}
		return InferenceFromSourceUtil.isReturnTypeCompatible(returnType, contract.returnValue);
	}

	private static boolean hasContradictoryExplicitParameterNullity(@Nonnull PsiMethod method, StandardMethodContract contract)
	{
		for(int i = 0; i < contract.arguments.length; i++)
		{
			if(contract.arguments[i] == NULL_VALUE && NullableNotNullManager.isNotNull(method.getParameterList().getParameters()[i]))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isContradictingExplicitNullableReturn(@Nonnull PsiMethod method, StandardMethodContract contract)
	{
		return contract.returnValue == NOT_NULL_VALUE && Arrays.stream(contract.arguments).allMatch(c -> c == ANY_VALUE) && NullableNotNullManager.getInstance(method.getProject()).isNullable(method,
				false);
	}

	private static boolean isReturnNullitySpecifiedExplicitly(@Nonnull PsiMethod method, StandardMethodContract contract)
	{
		if(contract.returnValue != NOT_NULL_VALUE && contract.returnValue != NULL_VALUE)
		{
			return false; // spare expensive nullity check
		}
		return NullableNotNullManager.getInstance(method.getProject()).isNotNull(method, false);
	}

	@Nonnull
	private static List<StandardMethodContract> boxReturnValues(List<StandardMethodContract> contracts)
	{
		return ContainerUtil.mapNotNull(contracts, contract ->
		{
			if(contract.returnValue == FALSE_VALUE || contract.returnValue == TRUE_VALUE)
			{
				return new StandardMethodContract(contract.arguments, NOT_NULL_VALUE);
			}
			return contract;
		});
	}

}