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

import java.util.List;

import jakarta.annotation.Nonnull;
import consulo.util.dataholder.Key;
import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.PsiCallExpression;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiExpressionList;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.filter.ElementFilter;
import com.intellij.java.language.psi.infos.CandidateInfo;
import com.intellij.java.language.impl.psi.scope.ElementClassFilter;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.psi.scope.JavaScopeProcessorEvent;
import com.intellij.java.language.impl.psi.scope.PsiConflictResolver;
import com.intellij.java.language.psi.util.PsiUtil;
import jakarta.annotation.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Dec 12, 2002
 * Time: 8:24:29 PM
 * To change this template use Options | File Templates.
 */
public abstract class MethodsProcessor extends ConflictFilterProcessor implements ElementClassHint
{
	private static final ElementFilter ourFilter = ElementClassFilter.METHOD;

	private boolean myStaticScopeFlag;
	private boolean myIsConstructor;
	protected PsiElement myCurrentFileContext;
	protected PsiClass myAccessClass;
	private PsiExpressionList myArgumentList;
	private PsiType[] myTypeArguments;
	private final LanguageLevel myLanguageLevel;

	public MethodsProcessor(@Nonnull PsiConflictResolver[] resolvers, @Nonnull List<CandidateInfo> container, @Nonnull PsiElement place, @Nonnull PsiFile placeFile)
	{
		super(null, ourFilter, resolvers, container, place, placeFile);
		myLanguageLevel = PsiUtil.getLanguageLevel(placeFile);
	}

	public PsiExpressionList getArgumentList()
	{
		return myArgumentList;
	}

	public void setArgumentList(@Nullable PsiExpressionList argList)
	{
		myArgumentList = argList;
	}

	@Nonnull
	public LanguageLevel getLanguageLevel()
	{
		return myLanguageLevel;
	}

	public void obtainTypeArguments(@Nonnull PsiCallExpression callExpression)
	{
		final PsiType[] typeArguments = callExpression.getTypeArguments();
		if(typeArguments.length > 0)
		{
			setTypeArguments(typeArguments);
		}
	}

	protected void setTypeArguments(PsiType[] typeParameters)
	{
		myTypeArguments = typeParameters;
	}

	public PsiType[] getTypeArguments()
	{
		return myTypeArguments;
	}

	public boolean isInStaticScope()
	{
		return myStaticScopeFlag;
	}

	@Override
	public void handleEvent(@Nonnull Event event, Object associated)
	{
		if(event == JavaScopeProcessorEvent.START_STATIC)
		{
			myStaticScopeFlag = true;
		}
		else if(JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT.equals(event))
		{
			myCurrentFileContext = (PsiElement) associated;
		}
	}

	public void setAccessClass(PsiClass accessClass)
	{
		myAccessClass = accessClass;
	}

	public boolean isConstructor()
	{
		return myIsConstructor;
	}

	public void setIsConstructor(boolean myIsConstructor)
	{
		this.myIsConstructor = myIsConstructor;
	}

	public void forceAddResult(@Nonnull PsiMethod method)
	{
		add(new CandidateInfo(method, PsiSubstitutor.EMPTY, false, false, myCurrentFileContext));
	}

	@Override
	public <T> T getHint(@Nonnull Key<T> hintKey)
	{
		if(hintKey == ElementClassHint.KEY)
		{
			return (T) this;
		}

		return super.getHint(hintKey);
	}

	@Override
	public boolean shouldProcess(DeclarationKind kind)
	{
		return kind == DeclarationKind.METHOD;
	}
}
