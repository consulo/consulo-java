// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.impl.psi.impl.cache.TypeInfo;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface PsiMethodStub extends PsiMemberStub<PsiMethod>
{
	boolean isConstructor();

	boolean isVarArgs();

	boolean isAnnotationMethod();

	@Nullable
	String getDefaultValueText();

	@Nonnull
	TypeInfo getReturnTypeText();

	PsiParameterStub findParameter(int idx);
}
