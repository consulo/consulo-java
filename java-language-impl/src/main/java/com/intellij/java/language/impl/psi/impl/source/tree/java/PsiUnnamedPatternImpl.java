// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiTypeElement;
import com.intellij.java.language.psi.PsiUnnamedPattern;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElementVisitor;
import jakarta.annotation.Nonnull;


public class PsiUnnamedPatternImpl extends CompositePsiElement implements PsiUnnamedPattern, Constants {
  public PsiUnnamedPatternImpl() {
    super(UNNAMED_PATTERN);
  }

  @Override
  public @Nonnull
  PsiTypeElement getTypeElement() {
    PsiTypeElement type = (PsiTypeElement)findPsiChildByType(JavaElementType.TYPE);
    assert type != null; // guaranteed by parser
    return type;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitUnnamedPattern(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiUnnamedPattern";
  }
}

