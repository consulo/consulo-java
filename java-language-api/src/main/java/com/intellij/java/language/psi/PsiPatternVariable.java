// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import javax.annotation.Nonnull;

/**
 * A variable declared within the pattern
 */
public interface PsiPatternVariable extends PsiParameter
{
	@Nonnull
	@Override
	String getName();

	@Nonnull
	@Override
	PsiTypeElement getTypeElement();

	@Nonnull
	@Override
	PsiIdentifier getNameIdentifier();

	@Nonnull
	PsiPattern getPattern();
}
