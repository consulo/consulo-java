// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;

public interface ClsCustomNavigationPolicy
{
	ExtensionPointName<ClsCustomNavigationPolicy> EP_NAME = ExtensionPointName.create("consulo.java.psi.clsCustomNavigationPolicy");

	@Nullable
	default PsiElement getNavigationElement(@SuppressWarnings("unused") @Nonnull ClsFileImpl clsFile)
	{
		return null;
	}

	@Nullable
	default PsiElement getNavigationElement(@SuppressWarnings("unused") @Nonnull ClsClassImpl clsClass)
	{
		return null;
	}

	@Nullable
	default PsiElement getNavigationElement(@SuppressWarnings("unused") @Nonnull ClsMethodImpl clsMethod)
	{
		return null;
	}

	@Nullable
	default PsiElement getNavigationElement(@SuppressWarnings("unused") @Nonnull ClsFieldImpl clsField)
	{
		return null;
	}
}