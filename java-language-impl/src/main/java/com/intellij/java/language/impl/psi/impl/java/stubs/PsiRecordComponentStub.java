// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.psi.PsiRecordComponent;
import com.intellij.java.language.impl.psi.impl.cache.TypeInfo;
import javax.annotation.Nonnull;

public interface PsiRecordComponentStub extends PsiMemberStub<PsiRecordComponent>
{
	@Override
	@Nonnull
	String getName();

	TypeInfo getType();

	boolean isVararg();
}