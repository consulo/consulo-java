/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.util.IncorrectOperationException;

public class PsiPackageReference extends PsiPolyVariantReferenceBase<PsiElement> implements EmptyResolveMessageProvider
{

	private final PackageReferenceSet myReferenceSet;
	private final int myIndex;

	public PsiPackageReference(final PackageReferenceSet set, final TextRange range, final int index)
	{
		super(set.getElement(), range, set.isSoft());
		myReferenceSet = set;
		myIndex = index;
	}

	@Nonnull
	private Set<PsiJavaPackage> getContext()
	{
		if(myIndex == 0)
		{
			return myReferenceSet.getInitialContext();
		}
		Set<PsiJavaPackage> psiPackages = new HashSet<PsiJavaPackage>();
		for(ResolveResult resolveResult : myReferenceSet.getReference(myIndex - 1).doMultiResolve())
		{
			PsiElement psiElement = resolveResult.getElement();
			if(psiElement instanceof PsiJavaPackage)
			{
				psiPackages.add((PsiJavaPackage) psiElement);
			}
		}
		return psiPackages;
	}

	@Override
	@Nonnull
	public Object[] getVariants()
	{
		Set<PsiJavaPackage> subPackages = new HashSet<PsiJavaPackage>();
		for(PsiJavaPackage psiPackage : getContext())
		{
			subPackages.addAll(Arrays.asList(psiPackage.getSubPackages(myReferenceSet.getResolveScope())));
		}

		return subPackages.toArray();
	}

	@Nonnull
	@Override
	public String getUnresolvedMessagePattern()
	{
		return JavaErrorBundle.message("cannot.resolve.package");
	}

	@Override
	@Nonnull
	public ResolveResult[] multiResolve(final boolean incompleteCode)
	{
		return doMultiResolve();
	}

	@Nonnull
	protected ResolveResult[] doMultiResolve()
	{
		final Collection<PsiJavaPackage> packages = new HashSet<PsiJavaPackage>();
		for(PsiJavaPackage parentPackage : getContext())
		{
			packages.addAll(myReferenceSet.resolvePackageName(parentPackage, getValue()));
		}
		return PsiElementResolveResult.createResults(packages);
	}

	@Override
	public PsiElement bindToElement(@Nonnull final PsiElement element) throws IncorrectOperationException
	{
		if(!(element instanceof PsiJavaPackage))
		{
			throw new IncorrectOperationException("Cannot bind to " + element);
		}
		final String newName = ((PsiJavaPackage) element).getQualifiedName();
		final TextRange range = new TextRange(getReferenceSet().getReference(0).getRangeInElement().getStartOffset(), getRangeInElement().getEndOffset());
		final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(getElement());
		return manipulator.handleContentChange(getElement(), range, newName);
	}

	public PackageReferenceSet getReferenceSet()
	{
		return myReferenceSet;
	}
}
