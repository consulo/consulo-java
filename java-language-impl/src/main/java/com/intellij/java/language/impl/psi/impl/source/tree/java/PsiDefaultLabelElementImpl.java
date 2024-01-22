// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source.tree.java;

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiDefaultCaseLabelElement;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElementVisitor;
import jakarta.annotation.Nonnull;

public class PsiDefaultLabelElementImpl extends CompositePsiElement implements PsiDefaultCaseLabelElement, Constants {
  public PsiDefaultLabelElementImpl() {
    super(DEFAULT_CASE_LABEL_ELEMENT);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDefaultCaseLabelElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiDefaultLabelElement";
  }
}

