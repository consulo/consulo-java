// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi.javadoc;

import consulo.language.Language;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Describes a markdown code block
 */
public interface PsiMarkdownCodeBlock extends PsiElement {

  /**
   * @return The text of the code block, without superfluous elements
   */
  String getCodeText();

  /**
   * @return The language for this code block. An inline code block has no language info
   */
  @Nullable Language getCodeLanguage();

  /**
   * @return Whether the code block is a single line block
   */
  boolean isInline();
}
