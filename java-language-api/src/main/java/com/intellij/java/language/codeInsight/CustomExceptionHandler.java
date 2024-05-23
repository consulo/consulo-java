// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.codeInsight;

import com.intellij.java.language.psi.PsiClassType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Allow to specify that exception produced by the element is handled.
 * <p>
 * Such elements won't be highlighted as unhandled.
 */
@ExtensionAPI(ComponentScope.PROJECT)
public interface CustomExceptionHandler {
  /**
   * Checks if the exception produced by element is handled somehow
   *
   * @param element       place which produces exception (for example {@link com.intellij.java.language.psi.PsiCall})
   * @param exceptionType type of produced exception
   * @param topElement    element at which exception should be handled
   */
  boolean isHandled(@Nullable PsiElement element, @Nonnull PsiClassType exceptionType, PsiElement topElement);
}
