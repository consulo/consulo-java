// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.psi.*;
import jakarta.inject.Singleton;
import javax.annotation.Nonnull;

import java.util.Map;

@Singleton
public class PsiSubstitutorFactoryImpl extends PsiSubstitutorFactory
{
	@Nonnull
	@Override
	protected PsiSubstitutor createSubstitutor(@Nonnull PsiTypeParameter typeParameter, PsiType mapping)
	{
		return new PsiSubstitutorImpl(typeParameter, mapping);
	}

	@Nonnull
	@Override
	protected PsiSubstitutor createSubstitutor(@Nonnull PsiClass aClass, PsiType[] mappings)
	{
		return new PsiSubstitutorImpl(aClass, mappings);
	}

	@Nonnull
	@Override
	protected PsiSubstitutor createSubstitutor(@Nonnull Map<? extends PsiTypeParameter, ? extends PsiType> map)
	{
		return new PsiSubstitutorImpl(map);
	}
}
