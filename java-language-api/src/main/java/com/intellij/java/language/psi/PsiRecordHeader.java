// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import consulo.language.psi.PsiElement;

import org.jspecify.annotations.Nullable;

public interface PsiRecordHeader extends PsiElement {
  PsiRecordComponent[] getRecordComponents();

  @Nullable
  PsiClass getContainingClass();
}
