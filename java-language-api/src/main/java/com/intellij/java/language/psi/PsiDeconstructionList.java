// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.psi;

import consulo.language.psi.PsiElement;

/**
 * Represents elements between '(' and ')' (inclusive) in {@link PsiDeconstructionPattern}.
 */
public interface PsiDeconstructionList extends PsiElement {
  PsiPattern[] getDeconstructionComponents();
}
