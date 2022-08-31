// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

interface ClassReferencePointer
{
	@Nullable
	PsiJavaCodeReferenceElement retrieveReference();

	@Nonnull
	PsiJavaCodeReferenceElement retrieveNonNullReference();

	static ClassReferencePointer constant(@Nonnull PsiJavaCodeReferenceElement ref)
	{
		return new ClassReferencePointer()
		{
			@Nonnull
			@Override
			public PsiJavaCodeReferenceElement retrieveReference()
			{
				return ref;
			}

			@Nonnull
			@Override
			public PsiJavaCodeReferenceElement retrieveNonNullReference()
			{
				return ref;
			}
		};
	}
}
