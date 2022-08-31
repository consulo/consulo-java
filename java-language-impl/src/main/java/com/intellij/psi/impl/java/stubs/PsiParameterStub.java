// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.java.language.psi.PsiParameter;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.stubs.NamedStub;
import javax.annotation.Nonnull;

public interface PsiParameterStub extends NamedStub<PsiParameter>
{
	@Nonnull
	@Override
	String getName();

	boolean isParameterTypeEllipsis();

	@Nonnull
	TypeInfo getType();

	PsiModifierListStub getModList();
}