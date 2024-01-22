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
package com.intellij.java.language.impl.psi.scope.processor;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiCallExpression;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpressionList;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import consulo.language.psi.resolve.ResolveState;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.psi.scope.JavaScopeProcessorEvent;
import com.intellij.java.language.impl.psi.scope.PsiConflictResolver;
import com.intellij.java.language.impl.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.util.collection.SmartList;

public class MethodResolverProcessor extends MethodCandidatesProcessor
{
	private boolean myStopAcceptingCandidates;

	public MethodResolverProcessor(@jakarta.annotation.Nonnull PsiMethodCallExpression place, @Nonnull PsiFile placeFile)
	{
		this(place, place.getArgumentList(), placeFile);
	}

	public MethodResolverProcessor(@jakarta.annotation.Nonnull PsiCallExpression place, @jakarta.annotation.Nonnull PsiExpressionList argumentList, @Nonnull PsiFile placeFile)
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

	public MethodResolverProcessor(@jakarta.annotation.Nonnull PsiElement place, @Nonnull PsiFile placeFile, @Nonnull PsiConflictResolver[] resolvers)
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
	public boolean execute(@jakarta.annotation.Nonnull PsiElement element, @jakarta.annotation.Nonnull ResolveState state)
	{
		return !myStopAcceptingCandidates && super.execute(element, state);
	}

	@Override
	protected boolean acceptVarargs()
	{
		return true;
	}
}
