// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

/**
 * Represents a reference to some module inside a Java module declaration.
 */
public interface PsiJavaModuleReferenceElement extends PsiElement {
  @Nonnull
  String getReferenceText();

  @Override
  @Nullable
  PsiJavaModuleReference getReference();
}