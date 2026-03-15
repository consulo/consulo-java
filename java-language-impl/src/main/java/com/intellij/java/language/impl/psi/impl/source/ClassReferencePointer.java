// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import org.jspecify.annotations.Nullable;

interface ClassReferencePointer
{
	@Nullable
	PsiJavaCodeReferenceElement retrieveReference();

	PsiJavaCodeReferenceElement retrieveNonNullReference();

	static ClassReferencePointer constant(PsiJavaCodeReferenceElement ref)
	{
		return new ClassReferencePointer()
		{
			@Override
			public PsiJavaCodeReferenceElement retrieveReference()
			{
				return ref;
			}

			@Override
			public PsiJavaCodeReferenceElement retrieveNonNullReference()
			{
				return ref;
			}
		};
	}
}
