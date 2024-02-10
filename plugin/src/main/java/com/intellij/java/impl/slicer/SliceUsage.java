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
package com.intellij.java.impl.slicer;

import consulo.language.editor.scope.AnalysisScope;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.function.Computable;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.usage.UsageInfo;
import consulo.usage.UsageInfo2UsageAdapter;
import consulo.application.util.function.CommonProcessors;
import consulo.application.util.function.Processor;
import consulo.util.collection.HashingStrategy;

import jakarta.annotation.Nonnull;

/**
 * @author cdr
 */
public class SliceUsage extends UsageInfo2UsageAdapter
{
	private final SliceUsage myParent;
	public final SliceAnalysisParams params;
	private final PsiSubstitutor mySubstitutor;
	protected final int indexNesting; // 0 means bare expression 'x', 1 means x[?], 2 means x[?][?] etc
	@Nonnull
	protected final String syntheticField; // "" means no field, otherwise it's a name of fake field of container, e.g. "keys" for Map

	public SliceUsage(@Nonnull PsiElement element,
			@Nonnull SliceUsage parent,
			@Nonnull PsiSubstitutor substitutor,
			int indexNesting,
			@Nonnull String syntheticField)
	{
		super(new UsageInfo(element));
		myParent = parent;
		mySubstitutor = substitutor;
		this.syntheticField = syntheticField;
		params = parent.params;
		assert params != null;
		this.indexNesting = indexNesting;
	}

	// root usage
	private SliceUsage(@Nonnull PsiElement element, @Nonnull SliceAnalysisParams params)
	{
		super(new UsageInfo(element));
		myParent = null;
		this.params = params;
		mySubstitutor = PsiSubstitutor.EMPTY;
		indexNesting = 0;
		syntheticField = "";
	}

	@Nonnull
	public static SliceUsage createRootUsage(@Nonnull PsiElement element, @Nonnull SliceAnalysisParams params)
	{
		return new SliceUsage(element, params);
	}

	public void processChildren(@Nonnull Processor<SliceUsage> processor)
	{
		final PsiElement element = ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>()
		{
			@Override
			public PsiElement compute()
			{
				return getElement();
			}
		});
		ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
		indicator.checkCanceled();

		final Processor<SliceUsage> uniqueProcessor = new CommonProcessors.UniqueProcessor<SliceUsage>(processor, new HashingStrategy<SliceUsage>()
		{
			@Override
			public int hashCode(final SliceUsage object)
			{
				return object.getUsageInfo().hashCode();
			}

			@Override
			public boolean equals(final SliceUsage o1, final SliceUsage o2)
			{
				return o1.getUsageInfo().equals(o2.getUsageInfo());
			}
		});

		ApplicationManager.getApplication().runReadAction(new Runnable()
		{
			@Override
			public void run()
			{
				if(params.dataFlowToThis)
				{
					SliceUtil.processUsagesFlownDownTo(element, uniqueProcessor, SliceUsage.this, mySubstitutor, indexNesting, syntheticField);
				}
				else
				{
					SliceForwardUtil.processUsagesFlownFromThe(element, uniqueProcessor, SliceUsage.this);
				}
			}
		});
	}

	public SliceUsage getParent()
	{
		return myParent;
	}

	@Nonnull
	public AnalysisScope getScope()
	{
		return params.scope;
	}

	@Nonnull
	SliceUsage copy()
	{
		PsiElement element = getUsageInfo().getElement();
		return getParent() == null ? createRootUsage(element, params) : new SliceUsage(element, getParent(), mySubstitutor, indexNesting,
				syntheticField);
	}

	@Nonnull
	public PsiSubstitutor getSubstitutor()
	{
		return mySubstitutor;
	}
}
