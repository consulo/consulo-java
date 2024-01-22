// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi.javadoc;

import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;

/**
 * Represents name-value pair in @snippet tag.
 *
 * @see PsiSnippetDocTag
 */
public interface PsiSnippetAttribute extends PsiElement {
  /**
   * Snippet ID (adds an anchor to generated javadoc)
   */
  String ID_ATTRIBUTE = "id";

  /**
   * External class location (relative to {@link #SNIPPETS_FOLDER})
   */
  String CLASS_ATTRIBUTE = "class";

  /**
   * External file path (relative to {@link #SNIPPETS_FOLDER})
   */
  String FILE_ATTRIBUTE = "file";

  /**
   * Region to render
   */
  String REGION_ATTRIBUTE = "region";

  /**
   * Language of the snippet
   */
  String LANG_ATTRIBUTE = "lang";

  /**
   * Default folder name to contain external snippets
   */
  String SNIPPETS_FOLDER = "snippet-files";

  PsiSnippetAttribute[] EMPTY_ARRAY = new PsiSnippetAttribute[0];

  /**
   * @return name element of this name-value pair.
   */
  @Contract(pure = true)
  @Nonnull
  PsiElement getNameIdentifier();

  /**
   * @return name of this name-value pair.
   */
  @Contract(pure = true)
  @Nonnull
  String getName();

  /**
   * @return value of this name-value pair or null if absent.
   */
  @Contract(pure = true)
  @Nullable
  PsiSnippetAttributeValue getValue();
}
