// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.psi;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;

/**
 * @author Vitaliy.Bibaev
 */
public final class PsiUtil {
  private PsiUtil() {}

  public static @Nonnull PsiElement ignoreWhiteSpaces(@Nonnull PsiElement element) {
    PsiElement result = PsiTreeUtil.skipSiblingsForward(element, PsiWhiteSpace.class);
    if (result == null) {
      result = PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace.class);
      if (result == null) {
        result = element;
      }
    }

    return result;
  }
}
