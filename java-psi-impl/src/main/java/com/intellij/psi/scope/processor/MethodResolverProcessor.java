/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.scope.processor;

import javax.annotation.Nonnull;

import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.ResolveState;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;

public class MethodResolverProcessor extends MethodCandidatesProcessor
{
	private boolean myStopAcceptingCandidates;

	public MethodResolverProcessor(@Nonnull PsiMethodCallExpression place, @Nonnull PsiFile placeFile)
	{
		this(place, place.getArgumentList(), placeFile);
	}

	public MethodResolverProcessor(@Nonnull PsiCallExpression place, @Nonnull PsiExpressionList argumentList, @Nonnull PsiFile placeFile)
	{
		this(place, placeFile, new PsiConflictResolver[]{new JavaMethodsConflictResolver(argumentList, PsiUtil.getLanguageLevel(placeFile))});
		setArgumentList(argumentList);
		obtainTypeArguments(place);
	}

	public MethodResolverProcessor(PsiClass classConstr, @Nonnull PsiExpressionList argumentList, @Nonnull PsiElement place, @Nonnull PsiFile placeFile)
	{
		super(place, placeFile, new PsiConflictResolver[]{
				new JavaMethodsConflictResolver(argumentList, PsiUtil.getLanguageLevel(placeFile))
		}, new SmartList<CandidateInfo>());
		setIsConstructor(true);
		setAccessClass(classConstr);
		setArgumentList(argumentList);
	}

	public MethodResolverProcessor(@Nonnull PsiElement place, @Nonnull PsiFile placeFile, @Nonnull PsiConflictResolver[] resolvers)
	{
		super(place, placeFile, resolvers, new SmartList<CandidateInfo>());
	}

	@Override
	public void handleEvent(@Nonnull Event event, Object associated)
	{
		if(event == JavaScopeProcessorEvent.CHANGE_LEVEL)
		{
			if(myHasAccessibleStaticCorrectCandidate)
			{
				myStopAcceptingCandidates = true;
			}
		}
		super.handleEvent(event, associated);
	}

	@Override
	public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state)
	{
		return !myStopAcceptingCandidates && super.execute(element, state);
	}

	@Override
	protected boolean acceptVarargs()
	{
		return true;
	}
}
