// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.javadoc.PsiSnippetDocTagBody;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import jakarta.annotation.Nonnull;

public class PsiSnippetDocTagBodyImpl extends CompositePsiElement implements PsiSnippetDocTagBody {
  public PsiSnippetDocTagBodyImpl() {
    super(JavaDocElementType.DOC_SNIPPET_BODY);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    super.accept(visitor);
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetDocTagBody(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSnippetDocTagBody";
  }

  @Override
  @Nonnull
  public PsiElement[] getContent() {
    return getChildrenAsPsiElements(JavaDocTokenType.DOC_COMMENT_DATA, PsiElement.ARRAY_FACTORY);
  }
}
