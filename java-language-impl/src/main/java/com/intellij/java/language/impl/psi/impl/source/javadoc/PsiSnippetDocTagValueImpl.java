// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.javadoc.PsiSnippetAttributeList;
import com.intellij.java.language.psi.javadoc.PsiSnippetDocTagBody;
import com.intellij.java.language.psi.javadoc.PsiSnippetDocTagValue;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

public class PsiSnippetDocTagValueImpl extends CompositePsiElement implements PsiSnippetDocTagValue {
  public PsiSnippetDocTagValueImpl() {
    super(JavaDocElementType.DOC_SNIPPET_TAG_VALUE);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    super.accept(visitor);
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @Nonnull
  PsiSnippetAttributeList getAttributeList() {
    // always present but may be zero length
    return Objects.requireNonNull(PsiTreeUtil.getChildOfType(this, PsiSnippetAttributeList.class));
  }

  @Override
  public
  @Nullable
  PsiSnippetDocTagBody getBody() {
    return PsiTreeUtil.getChildOfType(this, PsiSnippetDocTagBody.class);
  }

  @Override
  public String toString() {
    return "PsiSnippetDocTagValue";
  }
}
