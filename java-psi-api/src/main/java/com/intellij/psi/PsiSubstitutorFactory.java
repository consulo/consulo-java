// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.application.Application;
import javax.annotation.Nonnull;

import java.util.Map;

public abstract class PsiSubstitutorFactory
{
	@Nonnull
	protected abstract PsiSubstitutor createSubstitutor(@Nonnull PsiTypeParameter typeParameter, PsiType mapping);

	@Nonnull
	protected abstract PsiSubstitutor createSubstitutor(@Nonnull PsiClass aClass, PsiType[] mappings);

	@Nonnull
	protected abstract PsiSubstitutor createSubstitutor(@Nonnull Map<? extends PsiTypeParameter, ? extends PsiType> map);

	static PsiSubstitutorFactory getInstance()
	{
		return Application.get().getInstance(PsiSubstitutorFactory.class);
	}
}
