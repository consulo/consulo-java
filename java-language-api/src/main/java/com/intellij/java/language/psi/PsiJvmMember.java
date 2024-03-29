// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.JvmMember;

import jakarta.annotation.Nullable;

/**
 * Not all PsiMember inheritors are JvmMembers, e.g. {@link PsiClassInitializer}.
 * This is a bridge interface between them.
 * <p/>
 * Known PsiMembers which are also JvmMembers:
 * {@link PsiClass}, {@link PsiField} and {@link PsiMethod}.
 */
public interface PsiJvmMember extends PsiMember, JvmMember, PsiJvmModifiersOwner
{
	@Override
	@Nullable
	PsiClass getContainingClass();
}
