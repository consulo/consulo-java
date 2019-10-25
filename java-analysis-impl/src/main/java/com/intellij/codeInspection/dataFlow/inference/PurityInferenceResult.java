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

package com.intellij.codeInspection.dataFlow.inference;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import consulo.annotations.RequiredReadAction;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * from kotlin
 */
class PurityInferenceResult
{
	private List<ExpressionRange> mutableRefs;
	@Nullable
	private ExpressionRange singleCall;

	PurityInferenceResult(List<ExpressionRange> mutableRefs, @Nullable ExpressionRange singleCall)
	{
		this.mutableRefs = mutableRefs;
		this.singleCall = singleCall;
	}

	public boolean isPure(PsiMethod method, Supplier<PsiCodeBlock> body)
	{
		return !mutatesNonLocals(method, body) && callsOnlyPureMethods(method, body);
	}

	public List<ExpressionRange> getMutableRefs()
	{
		return mutableRefs;
	}

	@Nullable
	public ExpressionRange getSingleCall()
	{
		return singleCall;
	}

	@RequiredReadAction
	private boolean mutatesNonLocals(PsiMethod method, Supplier<PsiCodeBlock> body)
	{
		for(ExpressionRange range : mutableRefs)
		{
			if(!isLocalVarReference(range.restoreExpression(body.get()), method))
			{
				return true;
			}
		}
		return false;
	}

	@RequiredReadAction
	private boolean callsOnlyPureMethods(PsiMethod currentMethod, Supplier<PsiCodeBlock> body)
	{
		if(singleCall == null)
		{
			return true;
		}
		PsiCall psiCall = (PsiCall) singleCall.restoreExpression(body.get());
		PsiMethod method = psiCall.resolveMethod();
		if(method != null)
		{
			return method.equals(currentMethod) || JavaMethodContractUtil.isPure(method);
		}
		else if(psiCall instanceof PsiNewExpression && psiCall.getArgumentList() != null && psiCall.getArgumentList().getExpressionCount() == 0)
		{

			PsiJavaCodeReferenceElement classOrAnonymousClassReference = ((PsiNewExpression) psiCall).getClassOrAnonymousClassReference();
			PsiElement resolved = classOrAnonymousClassReference == null ? null : classOrAnonymousClassReference.resolve();

			PsiClass psiClass = resolved instanceof PsiClass ? (PsiClass) resolved : null;

			if(psiClass != null)
			{
				PsiClass superClass = psiClass.getSuperClass();
				return superClass == null || CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName());
			}
		}

		return false;
	}

	@RequiredReadAction
	private boolean isLocalVarReference(PsiExpression expression, PsiMethod scope)
	{
		if(expression instanceof PsiReferenceExpression)
		{
			PsiElement resolve = ((PsiReferenceExpression) expression).resolve();
			return resolve instanceof PsiLocalVariable || resolve instanceof PsiParameter;
		}
		else if(expression instanceof PsiArrayAccessExpression)
		{
			PsiExpression arrayExpression = ((PsiArrayAccessExpression) expression).getArrayExpression();
			if(arrayExpression instanceof PsiReferenceExpression)
			{
				PsiElement resolve = ((PsiReferenceExpression) arrayExpression).resolve();
				return resolve instanceof PsiLocalVariable && isLocallyCreatedArray(scope, (PsiLocalVariable) resolve);
			}
		}
		return false;
	}

	private boolean isLocallyCreatedArray(PsiMethod scope, PsiLocalVariable target)
	{
		PsiExpression initializer = target.getInitializer();
		if(initializer != null & !(initializer instanceof PsiNewExpression))
		{
			return false;
		}

		for(PsiReference ref : ReferencesSearch.search(target, new LocalSearchScope(scope)).findAll())
		{
			if(ref instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting((PsiExpression) ref))
			{
				PsiAssignmentExpression assign = PsiTreeUtil.getParentOfType((PsiReferenceExpression) ref, PsiAssignmentExpression.class);
				if(assign == null || !(assign.getRExpression() instanceof PsiNewExpression))
				{
					return false;
				}
			}
		}
		return true;
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

		PurityInferenceResult that = (PurityInferenceResult) o;

		if(mutableRefs != null ? !mutableRefs.equals(that.mutableRefs) : that.mutableRefs != null)
		{
			return false;
		}
		if(singleCall != null ? !singleCall.equals(that.singleCall) : that.singleCall != null)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = mutableRefs != null ? mutableRefs.hashCode() : 0;
		result = 31 * result + (singleCall != null ? singleCall.hashCode() : 0);
		return result;
	}
}
