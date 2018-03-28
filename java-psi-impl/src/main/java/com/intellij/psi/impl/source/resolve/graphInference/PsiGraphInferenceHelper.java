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
package com.intellij.psi.impl.source.resolve.graphInference;

import javax.annotation.Nonnull;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.util.PsiUtil;

/**
 * User: anna
 */
public class PsiGraphInferenceHelper implements PsiInferenceHelper
{
	private final PsiManager myManager;

	public PsiGraphInferenceHelper(PsiManager manager)
	{
		myManager = manager;
	}

	@Override
	public PsiType inferTypeForMethodTypeParameter(@Nonnull PsiTypeParameter typeParameter,
			@Nonnull PsiParameter[] parameters,
			@Nonnull PsiExpression[] arguments,
			@Nonnull PsiSubstitutor partialSubstitutor,
			@javax.annotation.Nullable PsiElement parent,
			@Nonnull ParameterTypeInferencePolicy policy)
	{
		final PsiSubstitutor substitutor;
		if(parent != null)
		{
			substitutor = inferTypeArguments(new PsiTypeParameter[]{typeParameter}, parameters, arguments, partialSubstitutor, parent, policy, PsiUtil.getLanguageLevel(parent));
		}
		else
		{
			final InferenceSession inferenceSession = new InferenceSession(new PsiTypeParameter[]{typeParameter}, partialSubstitutor, myManager, null);
			inferenceSession.initExpressionConstraints(parameters, arguments, null);
			substitutor = inferenceSession.infer();
		}
		return substitutor.substitute(typeParameter);
	}

	@Nonnull
	@Override
	public PsiSubstitutor inferTypeArguments(@Nonnull PsiTypeParameter[] typeParameters,
			@Nonnull PsiParameter[] parameters,
			@Nonnull PsiExpression[] arguments,
			@Nonnull PsiSubstitutor partialSubstitutor,
			@Nonnull PsiElement parent,
			@Nonnull ParameterTypeInferencePolicy policy,
			@Nonnull LanguageLevel languageLevel)
	{
		if(typeParameters.length == 0)
		{
			return partialSubstitutor;
		}

		return InferenceSessionContainer.infer(typeParameters, parameters, arguments, partialSubstitutor, parent, policy);
	}

	@Nonnull
	@Override
	public PsiSubstitutor inferTypeArguments(@Nonnull PsiTypeParameter[] typeParameters, @Nonnull PsiType[] leftTypes, @Nonnull PsiType[] rightTypes, @Nonnull LanguageLevel languageLevel)
	{
		if(typeParameters.length == 0)
		{
			return PsiSubstitutor.EMPTY;
		}
		InferenceSession session = new InferenceSession(typeParameters, leftTypes, rightTypes, PsiSubstitutor.EMPTY, myManager, null);
		for(PsiType leftType : leftTypes)
		{
			if(!session.isProperType(session.substituteWithInferenceVariables(leftType)))
			{
				return session.infer();
			}
		}
		for(PsiType rightType : rightTypes)
		{
			if(!session.isProperType(session.substituteWithInferenceVariables(rightType)))
			{
				return session.infer();
			}
		}
		return PsiSubstitutor.EMPTY;
	}

	@Override
	public PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam, PsiType param, PsiType arg, boolean isContraVariantPosition, LanguageLevel languageLevel)
	{
		if(PsiType.VOID.equals(arg) || PsiType.VOID.equals(param))
		{
			return PsiType.NULL;
		}
		if(param instanceof PsiArrayType && arg instanceof PsiArrayType)
		{
			return getSubstitutionForTypeParameter(typeParam, ((PsiArrayType) param).getComponentType(), ((PsiArrayType) arg).getComponentType(), isContraVariantPosition, languageLevel);
		}

		if(!(param instanceof PsiClassType))
		{
			return PsiType.NULL;
		}
		if(arg == null)
		{
			return PsiType.NULL;
		}
		final PsiType[] leftTypes;
		final PsiType[] rightTypes;
		if(isContraVariantPosition)
		{
			leftTypes = new PsiType[]{param};
			rightTypes = new PsiType[]{arg};
		}
		else
		{
			leftTypes = new PsiType[]{arg};
			rightTypes = new PsiType[]{param};
		}
		final PsiTypeParameterListOwner owner = typeParam.getOwner();
		final PsiTypeParameter[] typeParams = owner != null ? owner.getTypeParameters() : new PsiTypeParameter[]{typeParam};
		final InferenceSession inferenceSession = new InferenceSession(typeParams, leftTypes, rightTypes, PsiSubstitutor.EMPTY, myManager, null);
		if(inferenceSession.isProperType(inferenceSession.substituteWithInferenceVariables(param)) && inferenceSession.isProperType(inferenceSession.substituteWithInferenceVariables(arg)))
		{
			boolean proceed = false;
			for(PsiClassType classType : typeParam.getExtendsListTypes())
			{
				if(!inferenceSession.isProperType(inferenceSession.substituteWithInferenceVariables(classType)))
				{
					proceed = true;
					break;
				}
			}
			if(!proceed)
			{
				return PsiType.NULL;
			}
		}
		final PsiSubstitutor substitutor = inferenceSession.infer();
		return substitutor.substitute(typeParam);
	}
}
