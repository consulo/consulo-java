// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.light;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiRecordComponent;
import consulo.language.psi.SyntheticElement;
import javax.annotation.Nonnull;

public interface LightRecordMember extends PsiElement, SyntheticElement
{
	@Nonnull
	PsiRecordComponent getRecordComponent();
}
