// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.javadoc.PsiDocFragmentName;
import com.intellij.java.language.psi.javadoc.PsiDocFragmentRef;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

public class PsiDocFragmentRefImpl extends CompositePsiElement implements PsiDocFragmentRef, Constants {
  public PsiDocFragmentRefImpl() {
    super(DOC_FRAGMENT_REF);
  }

  @Override
  public void accept(PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @Nullable PsiClass getScope() {
    return PsiDocMethodOrFieldRef.getScope(this);
  }

  @Override
  public @Nullable PsiDocFragmentName getFragmentName() {
    return PsiTreeUtil.getChildOfType(this, PsiDocFragmentName.class);
  }
}
