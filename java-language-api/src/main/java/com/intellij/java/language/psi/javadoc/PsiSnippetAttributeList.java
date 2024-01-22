// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi.javadoc;

import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * List of attributes (for example {@code file} or {@code class}) of a given doc tag.
 *
 * @see PsiSnippetDocTag
 */
public interface PsiSnippetAttributeList extends PsiElement {
  /**
   * @return array of name-value pairs of snippet tag.
   */
  @Nonnull
  PsiSnippetAttribute[] getAttributes();

  /**
   * @param name name of the attribute to find
   * @return the first instance of attribute having a given name; null if no such attribute found
   */
  @Nullable
  PsiSnippetAttribute getAttribute(@Nonnull String name);
}
