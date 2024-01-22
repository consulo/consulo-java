// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.compiled;

import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;

/**
 * @deprecated use {@link ClsCustomNavigationPolicy} directly
 */
@Deprecated
@SuppressWarnings("DeprecatedIsStillUsed")
public abstract class ClsCustomNavigationPolicyEx implements ClsCustomNavigationPolicy {
  public PsiFile getFileNavigationElement(@Nonnull ClsFileImpl file) {
    return (PsiFile)getNavigationElement(file);
  }
}