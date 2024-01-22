// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.javadoc.PsiSnippetAttribute;
import com.intellij.java.language.psi.javadoc.PsiSnippetAttributeList;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class PsiSnippetAttributeListImpl extends CompositePsiElement implements PsiSnippetAttributeList {
  public PsiSnippetAttributeListImpl() {
    super(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_LIST);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    super.accept(visitor);
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetAttributeList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  @Nonnull
  public PsiSnippetAttribute[] getAttributes() {
    PsiSnippetAttribute[] children = PsiTreeUtil.getChildrenOfType(this, PsiSnippetAttribute.class);
    if (children == null) return PsiSnippetAttribute.EMPTY_ARRAY;
    return children;
  }

  @Override
  public
  @Nullable
  PsiSnippetAttribute getAttribute(@Nonnull String name) {
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiSnippetAttribute && ((PsiSnippetAttribute)child).getName().equals(name)) {
        return (PsiSnippetAttribute)child;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return "PsiSnippetAttributeList";
  }
}
