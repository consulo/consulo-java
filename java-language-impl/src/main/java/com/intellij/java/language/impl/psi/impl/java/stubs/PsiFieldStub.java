// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.impl.psi.impl.cache.TypeInfo;
import jakarta.annotation.Nonnull;

public interface PsiFieldStub extends PsiMemberStub<PsiField>
{
	String INITIALIZER_TOO_LONG = ";INITIALIZER_TOO_LONG;";
	String INITIALIZER_NOT_STORED = ";INITIALIZER_NOT_STORED;";

	@Nonnull
	TypeInfo getType();

	String getInitializerText();

	boolean isEnumConstant();
}