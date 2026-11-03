// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi.javadoc;

import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Represents the name of a fragment reference, for instance {@code my-id} in {@code Foo##my-id}.
 */
public interface PsiDocFragmentName extends PsiElement {

  /**
   * Returns the class that contains the fragment reference.
   */
  @Nullable PsiClass getScope();

}
