// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi.javadoc;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * The content of the snippet tag (attributes and inline body if present).
 *
 * @see PsiSnippetDocTag
 */
public interface PsiSnippetDocTagValue extends PsiDocTagValue {
  /**
   * @return list of name-value pairs of the snippet tag.
   */
  @Nonnull
  PsiSnippetAttributeList getAttributeList();

  /**
   * @return body (content) of the snippet tag if there is a body
   */
  @Nullable
  PsiSnippetDocTagBody getBody();
}
