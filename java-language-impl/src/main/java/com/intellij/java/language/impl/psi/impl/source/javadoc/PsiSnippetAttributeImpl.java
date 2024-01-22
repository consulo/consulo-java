// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.JavaDocTokenType;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.javadoc.PsiSnippetAttribute;
import com.intellij.java.language.psi.javadoc.PsiSnippetAttributeValue;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Objects;

public class PsiSnippetAttributeImpl extends CompositePsiElement implements PsiSnippetAttribute {
  public PsiSnippetAttributeImpl() {
    super(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE);
  }

  @Override
  public @Nonnull
  PsiElement getNameIdentifier() {
    return Objects.requireNonNull(findPsiChildByType(JavaDocTokenType.DOC_TAG_ATTRIBUTE_NAME));
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    super.accept(visitor);
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetAttribute(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String getName() {
    return getNameIdentifier().getText();
  }

  @Override
  public
  @Nullable
  PsiSnippetAttributeValue getValue() {
    return (PsiSnippetAttributeValue)findPsiChildByType(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_VALUE);
  }

  @Override
  public String toString() {
    return "PsiSnippetAttribute:" + getName();
  }
}
