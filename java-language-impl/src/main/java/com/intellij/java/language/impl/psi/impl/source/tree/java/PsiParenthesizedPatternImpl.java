// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiParenthesizedPattern;
import com.intellij.java.language.psi.PsiPattern;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class PsiParenthesizedPatternImpl extends CompositePsiElement implements PsiParenthesizedPattern, Constants {
  public PsiParenthesizedPatternImpl() {
    super(PARENTHESIZED_PATTERN);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParenthesizedPattern(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiParenthesizedPattern";
  }

  @Override
  public
  @Nullable
  PsiPattern getPattern() {
    return PsiTreeUtil.getChildOfType(this, PsiPattern.class);
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    final PsiPattern pattern = getPattern();
    if (pattern == null) return true;

    return pattern.processDeclarations(processor, state, null, place);
  }
}

