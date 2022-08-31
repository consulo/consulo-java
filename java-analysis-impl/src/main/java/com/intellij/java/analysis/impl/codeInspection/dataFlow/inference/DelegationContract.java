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
import com.intellij.java.analysis.impl.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.java.analysis.impl.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.java.language.psi.*;
import com.intellij.psi.*;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.util.ObjectUtil;
import com.intellij.util.containers.ContainerUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * from kotlin
 */
class DelegationContract implements PreContract
{
	private ExpressionRange expression;
	private boolean negated;

	DelegationContract(ExpressionRange expression, boolean negated)
	{
		this.expression = expression;
		this.negated = negated;
	}

	public ExpressionRange getExpression()
	{
		return expression;
	}

	public boolean isNegated()
	{
		return negated;
	}

	@Nonnull
	@Override
	public List<StandardMethodContract> toContracts(PsiMethod method, Supplier<PsiCodeBlock> body)
	{
		PsiMethodCallExpression call = ObjectUtil.tryCast(expression.restoreExpression(body.get()), PsiMethodCallExpression.class);
		if(call == null)
		{
			return Collections.emptyList();
		}

		JavaResolveResult result = call.resolveMethodGenerics();
		PsiMethod targetMethod = ObjectUtil.tryCast(result.getElement(), PsiMethod.class);
		if(targetMethod == null)
		{
			return Collections.emptyList();
		}
		PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
		PsiExpression[] arguments = call.getArgumentList().getExpressions();
		boolean varArgCall = MethodCallInstruction.isVarArgCall(targetMethod, result.getSubstitutor(), arguments, parameters);

		List<StandardMethodContract> methodContracts = StandardMethodContract.toNonIntersectingStandardContracts(JavaMethodContractUtil.getMethodContracts(targetMethod));
		if(methodContracts == null)
		{
			return Collections.emptyList();
		}

		List<StandardMethodContract> fromDelegate = ContainerUtil.mapNotNull(methodContracts, dc ->
		{
			return convertDelegatedMethodContract(method, parameters, arguments, varArgCall, dc);
		});

		if(NullableNotNullManager.isNotNull(targetMethod))
		{
			List<StandardMethodContract> map = ContainerUtil.map(fromDelegate, DelegationContract::returnNotNull);
			fromDelegate = ContainerUtil.append(map, new StandardMethodContract(emptyConstraints(method), ContractReturnValue.returnNotNull()));
		}
		return fromDelegate;
	}

	private static StandardMethodContract returnNotNull(StandardMethodContract mc)
	{
		if(mc.getReturnValue().isFail())
		{
			return mc;
		}
		else
		{
			return mc.withReturnValue(ContractReturnValue.returnNotNull());
		}
	}

	private StandardMethodContract convertDelegatedMethodContract(PsiMethod callerMethod,
																  PsiParameter[] targetParameters,
																  PsiExpression[] callArguments,
																  boolean varArgCall,
																  StandardMethodContract targetContract)
	{
		StandardMethodContract.ValueConstraint[] answer = emptyConstraints(callerMethod);

		for(int i = 0; i < targetContract.getParameterCount(); i++)
		{
			if(i >= callArguments.length)
			{
				return null;
			}

			StandardMethodContract.ValueConstraint argConstraint = targetContract.getParameterConstraint(i);

			if(argConstraint != StandardMethodContract.ValueConstraint.ANY_VALUE)
			{
				if(varArgCall && i >= targetParameters.length - 1)
				{
					if(argConstraint == StandardMethodContract.ValueConstraint.NULL_VALUE)
					{
						return null;
					}
					break;
				}

				PsiExpression argument = PsiUtil.skipParenthesizedExprDown(callArguments[i]);
				if(argument == null)
				{
					return null;
				}
				int paramIndex = resolveParameter(callerMethod, argument);
				if(paramIndex >= 0)
				{
					answer = ContractInferenceInterpreter.withConstraint(answer, paramIndex, argConstraint);
					if(answer == null)
					{
						return null;
					}
				}
				else if(argConstraint != getLiteralConstraint(argument))
				{
					return null;
				}
			}
		}

		ContractReturnValue returnValue = targetContract.getReturnValue();
		if(negated && returnValue instanceof ContractReturnValue.BooleanReturnValue)
		{
			returnValue = ((ContractReturnValue.BooleanReturnValue) returnValue).negate();
		}

		if(answer != null)
		{
			return new StandardMethodContract(answer, returnValue);
		}
		return null;
	}

	@Nullable
	private static StandardMethodContract.ValueConstraint getLiteralConstraint(PsiExpression argument)
	{
		if(argument instanceof PsiLiteralExpression)
		{
			return ContractInferenceInterpreter.getLiteralConstraint(argument.getFirstChild().getNode().getElementType());
		}
		else if(argument instanceof PsiNewExpression || argument instanceof PsiPolyadicExpression || argument instanceof PsiFunctionalExpression)
		{
			return StandardMethodContract.ValueConstraint.NOT_NULL_VALUE;
		}
		return null;
	}

	private static int resolveParameter(PsiMethod method, PsiExpression expr)
	{
		PsiElement target = null;
		if(expr instanceof PsiReferenceExpression && !((PsiReferenceExpression) expr).isQualified())
		{
			target = ((PsiReferenceExpression) expr).resolve();
		}

		if(target instanceof PsiParameter && target.getParent() == method.getParameterList())
		{
			return method.getParameterList().getParameterIndex((PsiParameter) target);
		}
		return -1;
	}

	private static StandardMethodContract.ValueConstraint[] emptyConstraints(PsiMethod method)
	{
		return StandardMethodContract.createConstraintArray(method.getParameterList().getParametersCount());
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

		DelegationContract that = (DelegationContract) o;

		if(negated != that.negated)
		{
			return false;
		}
		if(expression != null ? !expression.equals(that.expression) : that.expression != null)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = expression != null ? expression.hashCode() : 0;
		result = 31 * result + (negated ? 1 : 0);
		return result;
	}
}
