/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.util.lang.Comparing;
import consulo.util.lang.function.Condition;
import consulo.document.util.TextRange;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.ide.impl.psi.util.ReferenceSetBase;
import consulo.ide.impl.idea.util.NullableFunction;
import consulo.util.collection.ContainerUtil;

/**
 * @author Dmitry Avdeev
 */
public class PackageReferenceSet extends ReferenceSetBase<PsiPackageReference>
{
	private final GlobalSearchScope mySearchScope;

	public PackageReferenceSet(@Nonnull final String str, @Nonnull final PsiElement element, final int startInElement)
	{
		this(str, element, startInElement, element.getResolveScope());
	}

	public PackageReferenceSet(@Nonnull final String str, @Nonnull final PsiElement element, final int startInElement, @Nonnull GlobalSearchScope scope)
	{
		super(str, element, startInElement, DOT_SEPARATOR);
		mySearchScope = scope;
	}

	@Override
	@Nonnull
	protected PsiPackageReference createReference(final TextRange range, final int index)
	{
		return new PsiPackageReference(this, range, index);
	}

	public Collection<PsiJavaPackage> resolvePackageName(@Nullable PsiJavaPackage context, final String packageName)
	{
		if(context != null)
		{
			return ContainerUtil.filter(context.getSubPackages(getResolveScope()), new Condition<PsiJavaPackage>()
			{
				@Override
				public boolean value(PsiJavaPackage aPackage)
				{
					return Comparing.equal(aPackage.getName(), packageName);
				}
			});
		}
		return Collections.emptyList();
	}

	@Nonnull
	protected GlobalSearchScope getResolveScope()
	{
		return mySearchScope;
	}

	public Collection<PsiJavaPackage> resolvePackage()
	{
		final PsiPackageReference packageReference = getLastReference();
		if(packageReference == null)
		{
			return Collections.emptyList();
		}
		return ContainerUtil.map2List(packageReference.multiResolve(false), new NullableFunction<ResolveResult, PsiJavaPackage>()
		{
			@Override
			public PsiJavaPackage fun(final ResolveResult resolveResult)
			{
				return (PsiJavaPackage) resolveResult.getElement();
			}
		});
	}

	public Set<PsiJavaPackage> getInitialContext()
	{
		return Collections.singleton(JavaPsiFacade.getInstance(getElement().getProject()).findPackage(""));
	}
}