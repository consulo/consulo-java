/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.filter.ElementFilter;
import consulo.language.psi.resolve.BaseScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nullable;

/**
 * @author ik
 *         Date: 13.02.2003
 */
public class FilterScopeProcessor<T> extends BaseScopeProcessor
{
	protected final List<T> myResults;
	private PsiElement myCurrentDeclarationHolder;
	private final ElementFilter myFilter;
	private final PsiScopeProcessor myProcessor;

	public FilterScopeProcessor(@Nonnull ElementFilter filter, @Nonnull List<T> container)
	{
		this(filter, null, container);
	}

	public FilterScopeProcessor(@Nonnull ElementFilter filter, @Nonnull PsiScopeProcessor processor)
	{
		this(filter, processor, new SmartList<T>());
	}

	public FilterScopeProcessor(@Nonnull ElementFilter filter)
	{
		this(filter, null, new SmartList<T>());
	}

	public FilterScopeProcessor(@Nonnull ElementFilter filter,
			@Nullable PsiScopeProcessor processor,
			@Nonnull List<T> container)
	{
		myFilter = filter;
		myProcessor = processor;
		myResults = container;
	}

	@Override
	public void handleEvent(@Nonnull PsiScopeProcessor.Event event, Object associated)
	{
		if(myProcessor != null)
		{
			myProcessor.handleEvent(event, associated);
		}
		if(event == PsiScopeProcessor.Event.SET_DECLARATION_HOLDER && associated instanceof PsiElement)
		{
			myCurrentDeclarationHolder = (PsiElement) associated;
		}
	}

	@Override
	public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state)
	{
		if(myFilter.isAcceptable(element, myCurrentDeclarationHolder))
		{
			if(myProcessor != null)
			{
				return myProcessor.execute(element, state);
			}
			add(element, state.get(PsiSubstitutor.KEY));
		}
		return true;
	}

	protected void add(@Nonnull PsiElement element, @Nonnull PsiSubstitutor substitutor)
	{
		//noinspection unchecked
		myResults.add((T) element);
	}

	@Override
	public <K> K getHint(@Nonnull Key<K> hintKey)
	{
		if(myProcessor != null)
		{
			return myProcessor.getHint(hintKey);
		}
		return null;
	}

	@Nonnull
	public List<T> getResults()
	{
		return myResults;
	}
}
