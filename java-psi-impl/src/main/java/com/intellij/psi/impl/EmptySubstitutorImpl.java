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
package com.intellij.psi.impl;

import java.util.Collections;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.EmptySubstitutor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;

/**
 * @author dsl
 */
public final class EmptySubstitutorImpl extends EmptySubstitutor
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.EmptySubstitutorImpl");

	@Override
	public PsiType substitute(@NotNull PsiTypeParameter typeParameter)
	{
		return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(typeParameter);
	}

	@Override
	public PsiType substitute(PsiType type)
	{
		return type;
	}

	@Override
	public PsiType substituteWithBoundsPromotion(@NotNull PsiTypeParameter typeParameter)
	{
		return JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(typeParameter);
	}

	@NotNull
	@Override
	public PsiSubstitutor put(@NotNull PsiTypeParameter classParameter, PsiType mapping)
	{
		if(mapping != null && !mapping.isValid())
		{
			LOG.error("Invalid type in substitutor: " + mapping);
		}
		return new PsiSubstitutorImpl(classParameter, mapping);
	}

	@NotNull
	@Override
	public PsiSubstitutor putAll(@NotNull PsiClass parentClass, PsiType[] mappings)
	{
		if(!parentClass.hasTypeParameters())
		{
			return this;
		}
		return new PsiSubstitutorImpl(parentClass, mappings);
	}

	@NotNull
	@Override
	public PsiSubstitutor putAll(@NotNull PsiSubstitutor another)
	{
		return another;
	}

	@Override
	@NotNull
	public Map<PsiTypeParameter, PsiType> getSubstitutionMap()
	{
		return Collections.emptyMap();
	}

	@Override
	public boolean isValid()
	{
		return true;
	}

	@Override
	public void ensureValid()
	{
	}
}