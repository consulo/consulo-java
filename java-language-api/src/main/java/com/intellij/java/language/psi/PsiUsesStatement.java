// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import jakarta.annotation.Nullable;

/**
 * Represents a {@code uses} directive of a Java module declaration.
 */
public interface PsiUsesStatement extends PsiStatement
{
	PsiUsesStatement[] EMPTY_ARRAY = new PsiUsesStatement[0];

	@Nullable
	PsiJavaCodeReferenceElement getClassReference();

	@Nullable
	PsiClassType getClassType();
}