// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.java.stubs;

import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.impl.psi.impl.cache.TypeInfo;
import consulo.language.psi.stub.NamedStub;
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