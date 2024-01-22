// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi;

import jakarta.annotation.Nonnull;

/**
 * Represents an unnamed pattern (single '_' inside deconstruction pattern, like {@code R(_)}).
 * Not to be confused with type pattern with unnamed variable (like {@code R(int _)})
 */
public interface PsiUnnamedPattern extends PsiPrimaryPattern {
  /**
   * @return implicit type element (empty)
   */
  @Nonnull
  PsiTypeElement getTypeElement();
}
