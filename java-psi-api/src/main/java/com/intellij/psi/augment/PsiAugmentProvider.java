/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.augment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.Processor;


public abstract class PsiAugmentProvider
{
	public static final ExtensionPointName<PsiAugmentProvider> EP_NAME = ExtensionPointName.create("consulo.java.augmentProvider");

	@Nonnull
	public abstract <Psi extends PsiElement> List<Psi> getAugments(@Nonnull PsiElement element, @Nonnull Class<Psi> type);

	@Nonnull
	public static <Psi extends PsiElement> List<Psi> collectAugments(@Nonnull final PsiElement element, @Nonnull final Class<Psi> type)
	{
		final List<Psi> augments = new ArrayList<Psi>();
		for(PsiAugmentProvider provider : Extensions.getExtensions(EP_NAME))
		{
			final List<Psi> list = provider.getAugments(element, type);
			augments.addAll(list);
		}

		return augments;
	}

	/**
	 * Intercepts {@link PsiModifierList#hasModifierProperty(String)}, so that plugins can add imaginary modifiers or hide existing ones.
	 *
	 * @since 2016.2
	 */
	@Nonnull
	protected Set<String> transformModifiers(@Nonnull PsiModifierList modifierList, @Nonnull final Set<String> modifiers)
	{
		return modifiers;
	}

	@Nonnull
	public static Set<String> transformModifierProperties(@Nonnull final PsiModifierList modifierList, @Nonnull Project project, @Nonnull final Set<String> modifiers)
	{
		final Ref<Set<String>> result = Ref.create(modifiers);

		forEach(project, provider ->
		{
			result.set(provider.transformModifiers(modifierList, Collections.unmodifiableSet(result.get())));
			return true;
		});

		return result.get();
	}

	private static void forEach(Project project, Processor<PsiAugmentProvider> processor)
	{
		boolean dumb = DumbService.isDumb(project);
		for(PsiAugmentProvider provider : Extensions.getExtensions(EP_NAME))
		{
			if(!dumb || DumbService.isDumbAware(provider))
			{
				try
				{
					boolean goOn = processor.process(provider);
					if(!goOn)
					{
						break;
					}
				}
				catch(ProcessCanceledException e)
				{
					throw e;
				}
				catch(Exception e)
				{
					Logger.getInstance(PsiAugmentProvider.class).error("provider: " + provider, e);
				}
			}
		}
	}
}
