// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi.javadoc;

import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Contract;

/**
 * Represents an attribute value for a snippet attribute
 *
 * @see PsiSnippetAttributeValue
 */
public interface PsiSnippetAttributeValue extends PsiElement {
  /**
   * Returns the content of the attribute value (without quotes, if any)
   */
  @Contract(pure = true)
  @Nonnull
  String getValue();
}
