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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.util.ObjectUtil;
import com.intellij.util.containers.ContainerUtil;

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

	@NotNull
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


		List<StandardMethodContract> fromDelegate = ContainerUtil.mapNotNull(ControlFlowAnalyzer.getMethodContracts(targetMethod), dc -> convertDelegatedMethodContract(method, parameters, arguments,
				varArgCall, dc));

		if(NullableNotNullManager.isNotNull(targetMethod))
		{
			return ContainerUtil.concat(ContainerUtil.map(fromDelegate, DelegationContract::returnNotNull), Collections.singletonList(new StandardMethodContract(emptyConstraints(method),
					MethodContract.ValueConstraint.NOT_NULL_VALUE)));
		}
		return fromDelegate;
	}

	private static StandardMethodContract returnNotNull(StandardMethodContract mc)
	{
		if(mc.getReturnValue() == MethodContract.ValueConstraint.THROW_EXCEPTION)
		{
			return mc;
		}
		else
		{
			return new StandardMethodContract(mc.arguments, MethodContract.ValueConstraint.NOT_NULL_VALUE);
		}
	}

	private StandardMethodContract convertDelegatedMethodContract(PsiMethod callerMethod,
			PsiParameter[] targetParameters,
			PsiExpression[] callArguments,
			boolean varArgCall,
			StandardMethodContract targetContract)
	{
		MethodContract.ValueConstraint[] answer = emptyConstraints(callerMethod);

		for(int i = 0; i < targetContract.arguments.length; i++)
		{
			if(i >= callArguments.length)
			{
				return null;
			}

			MethodContract.ValueConstraint argConstraint = targetContract.arguments[i];
			if(argConstraint != MethodContract.ValueConstraint.ANY_VALUE)
			{
				if(varArgCall && i >= targetParameters.length - 1)
				{
					if(argConstraint == MethodContract.ValueConstraint.NULL_VALUE)
					{
						return null;
					}
					break;
				}
			}

			PsiExpression argument = callArguments[i];
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

		MethodContract.ValueConstraint returnValue = negated ? targetContract.returnValue.negate() : targetContract.returnValue;
		return answer != null ? new StandardMethodContract(answer, returnValue) : null;
	}

	@Nullable
	private static MethodContract.ValueConstraint getLiteralConstraint(PsiExpression argument)
	{
		if(argument instanceof PsiLiteralExpression)
		{
			return ContractInferenceInterpreter.getLiteralConstraint(argument.getFirstChild().getNode().getElementType());
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

	private static MethodContract.ValueConstraint[] emptyConstraints(PsiMethod method)
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
