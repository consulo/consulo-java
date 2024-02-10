// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import com.intellij.java.language.jvm.JvmModifier;
import com.intellij.java.language.jvm.JvmModifiersOwner;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Not all PsiModifierListOwner inheritors are JvmModifiersOwners, e.g. {@link PsiLocalVariable} or {@link PsiRequiresStatement}.
 * This is a bridge interface between them.
 * <p>
 * Known PsiModifierListOwners which are also JvmModifiersOwners:
 * {@link PsiJvmMember} inheritors, {@link PsiParameter} and {@link PsiPackage}.
 */
public interface PsiJvmModifiersOwner extends PsiModifierListOwner, JvmModifiersOwner {
  @Override
  @Nonnull
  default PsiAnnotation[] getAnnotations() {
    return PsiModifierListOwner.super.getAnnotations();
  }

  @Nullable
  @Override
  default PsiAnnotation getAnnotation(@Nonnull String fqn) {
    return PsiModifierListOwner.super.getAnnotation(fqn);
  }

  @Override
  default boolean hasAnnotation(@Nonnull String fqn) {
    return PsiModifierListOwner.super.hasAnnotation(fqn);
  }

  @Override
  default boolean hasModifier(@Nonnull JvmModifier modifier) {
    return PsiModifierListOwner.super.hasModifier(modifier);
  }

  @Nullable
  @Override
  default PsiElement getSourceElement() {
    return this;
  }
}
