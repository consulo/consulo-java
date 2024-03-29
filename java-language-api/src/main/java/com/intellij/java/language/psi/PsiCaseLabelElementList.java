// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.psi;

import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

/**
 * Represents list of elements which goes after {@code case} when it contains patterns, null or default elements
 */
public interface PsiCaseLabelElementList extends PsiElement {
  /**
   * @return array of the elements contained in the list
   */
  @Nonnull
  PsiCaseLabelElement[] getElements();

  /**
   * @return count of the elements contained in the list
   */
  int getElementCount();
}
