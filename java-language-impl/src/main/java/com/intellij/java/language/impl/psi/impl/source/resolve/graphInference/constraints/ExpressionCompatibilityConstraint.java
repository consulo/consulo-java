/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.java.language.impl.psi.impl.source.resolve.graphInference.constraints;

import java.util.List;
import java.util.Set;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.impl.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.java.language.impl.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.java.language.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.java.language.psi.infos.MethodCandidateInfo;
import com.intellij.java.language.psi.util.TypeConversionUtil;

/**
 * User: anna
 */
public class ExpressionCompatibilityConstraint extends InputOutputConstraintFormula
{
	private final PsiExpression myExpression;
	private PsiType myT;

	public ExpressionCompatibilityConstraint(@Nonnull PsiExpression expression, @Nonnull PsiType type)
	{
		myExpression = expression;
		myT = type;
	}

	@Override
	public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints)
	{
		if(!PsiPolyExpressionUtil.isPolyExpression(myExpression))
		{
			if(session.isProperType(myT))
			{
				final boolean assignmentCompatible = TypeConversionUtil.areTypesAssignmentCompatible(myT, myExpression);
				if(!assignmentCompatible)
				{
					final PsiType type = myExpression.getType();
					session.registerIncompatibleErrorMessage((type != null ? type.getPresentableText() : myExpression.getText()) + " is not compatible with " + session.getPresentableText(myT));
				}
				return assignmentCompatible;
			}

			PsiType exprType = myExpression.getType();

			if(exprType instanceof PsiLambdaParameterType)
			{
				return false;
			}

			if(exprType instanceof PsiClassType)
			{
				if(((PsiClassType) exprType).resolve() == null)
				{
					return true;
				}
			}

			if(exprType != null && exprType != PsiType.NULL)
			{
				if(exprType instanceof PsiDisjunctionType)
				{
					exprType = ((PsiDisjunctionType) exprType).getLeastUpperBound();
				}

				constraints.add(new TypeCompatibilityConstraint(myT, exprType));
			}
			return true;
		}
		if(myExpression instanceof PsiParenthesizedExpression)
		{
			final PsiExpression expression = ((PsiParenthesizedExpression) myExpression).getExpression();
			if(expression != null)
			{
				constraints.add(new ExpressionCompatibilityConstraint(expression, myT));
				return true;
			}
		}

		if(myExpression instanceof PsiConditionalExpression)
		{
			final PsiExpression thenExpression = ((PsiConditionalExpression) myExpression).getThenExpression();
			if(thenExpression != null)
			{
				constraints.add(new ExpressionCompatibilityConstraint(thenExpression, myT));
			}

			final PsiExpression elseExpression = ((PsiConditionalExpression) myExpression).getElseExpression();
			if(elseExpression != null)
			{
				constraints.add(new ExpressionCompatibilityConstraint(elseExpression, myT));
			}
			return true;
		}

		if(myExpression instanceof PsiCall)
		{
			final InferenceSession callSession = reduceExpressionCompatibilityConstraint(session, myExpression, myT);
			if(callSession == null)
			{
				return false;
			}
			if(callSession != session)
			{
				session.getInferenceSessionContainer().registerNestedSession(callSession);
				session.propagateVariables(callSession.getInferenceVariables(), callSession.getRestoreNameSubstitution());
				if(callSession.isErased())
				{
					session.setErased();
				}
			}
			return true;
		}

		if(myExpression instanceof PsiMethodReferenceExpression)
		{
			constraints.add(new PsiMethodReferenceCompatibilityConstraint(((PsiMethodReferenceExpression) myExpression), myT));
			return true;
		}

		if(myExpression instanceof PsiLambdaExpression)
		{
			constraints.add(new LambdaExpressionCompatibilityConstraint((PsiLambdaExpression) myExpression, myT));
			return true;
		}


		return true;
	}

	public static InferenceSession reduceExpressionCompatibilityConstraint(InferenceSession session, PsiExpression expression, PsiType targetType)
	{
		final PsiExpressionList argumentList = ((PsiCall) expression).getArgumentList();
		if(argumentList != null)
		{
			final MethodCandidateInfo.CurrentCandidateProperties candidateProperties = MethodCandidateInfo.getCurrentMethod(argumentList);
			PsiType returnType = null;
			PsiTypeParameter[] typeParams = null;
			final JavaResolveResult resolveResult = candidateProperties != null ? null : InferenceSession.getResolveResult((PsiCall) expression);
			final PsiMethod method = InferenceSession.getCalledMethod((PsiCall) expression);

			if(method != null && !method.isConstructor())
			{
				returnType = method.getReturnType();
				if(returnType != null)
				{
					typeParams = method.getTypeParameters();
				}
			}
			else if(resolveResult != null)
			{
				final PsiClass psiClass = method != null ? method.getContainingClass() : (PsiClass) resolveResult.getElement();
				if(psiClass != null)
				{
					returnType = JavaPsiFacade.getElementFactory(argumentList.getProject()).createType(psiClass, PsiSubstitutor.EMPTY);
					typeParams = psiClass.getTypeParameters();
				}
			}

			if(typeParams != null)
			{
				PsiSubstitutor siteSubstitutor = InferenceSession.chooseSiteSubstitutor(candidateProperties, resolveResult, method);
				final InferenceSession callSession = new InferenceSession(typeParams, siteSubstitutor, expression.getManager(), expression);
				callSession.propagateVariables(session.getInferenceVariables(), session.getRestoreNameSubstitution());
				if(method != null)
				{
					final PsiExpression[] args = argumentList.getExpressions();
					final PsiParameter[] parameters = method.getParameterList().getParameters();
					callSession.initExpressionConstraints(parameters, args, expression, method, InferenceSession.chooseVarargsMode(candidateProperties, resolveResult));
				}
				if(callSession.repeatInferencePhases())
				{

					if(PsiType.VOID.equals(targetType))
					{
						return callSession;
					}

					callSession.registerReturnTypeConstraints(siteSubstitutor.substitute(returnType), targetType);
					if(callSession.repeatInferencePhases())
					{
						return callSession;
					}
				}

				//copy incompatible message if any
				final List<String> messages = callSession.getIncompatibleErrorMessages();
				if(messages != null)
				{
					for(String message : messages)
					{
						session.registerIncompatibleErrorMessage(message);
					}
				}
				return null;
			}
		}
		return session;
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

		ExpressionCompatibilityConstraint that = (ExpressionCompatibilityConstraint) o;

		if(!myExpression.equals(that.myExpression))
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		return myExpression.hashCode();
	}

	@Override
	public PsiExpression getExpression()
	{
		return myExpression;
	}

	@Override
	public PsiType getT()
	{
		return myT;
	}

	@Override
	protected void setT(PsiType t)
	{
		myT = t;
	}

	@Override
	protected InputOutputConstraintFormula createSelfConstraint(PsiType type, PsiExpression expression)
	{
		return new ExpressionCompatibilityConstraint(expression, type);
	}

	@Override
	protected void collectReturnTypeVariables(InferenceSession session, PsiExpression psiExpression, PsiType returnType, Set<InferenceVariable> result)
	{
		if(psiExpression instanceof PsiLambdaExpression)
		{
			if(!PsiType.VOID.equals(returnType))
			{
				final List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions((PsiLambdaExpression) psiExpression);
				for(PsiExpression expression : returnExpressions)
				{
					final Set<InferenceVariable> resultInputVars = createSelfConstraint(returnType, expression).getInputVariables(session);
					if(resultInputVars != null)
					{
						result.addAll(resultInputVars);
					}
				}
			}
		}
	}
}
