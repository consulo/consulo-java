// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.javadoc.PsiMarkdownReferenceLink;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import org.jspecify.annotations.Nullable;

import static com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType.DOC_MARKDOWN_REFERENCE_LINK;

public class PsiMarkdownReferenceLinkImpl extends CompositePsiElement implements PsiMarkdownReferenceLink {
  public PsiMarkdownReferenceLinkImpl() {
    super(DOC_MARKDOWN_REFERENCE_LINK);
  }

  @Override
  public void accept(PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMarkdownReferenceLink(this);
      return;
    }
    super.accept(visitor);
  }

  @Override
  public String toString() {
    return "PsiReferenceLink:";
  }

  @Override
  public @Nullable PsiElement getLabel() {
    // returns `null` malformed/incomplete link
    return getChildAt(1);
  }

  @Override
  public boolean isShortLink() {
    return countChildren(null) < 5;
  }

  @Override
  public @Nullable PsiElement getLinkElement() {
    int childrenCount = countChildren(null);
    return getChildAt(childrenCount - 2);
  }

  /**
   * Utility function, get PsiElement at specific index (if it exists)
   */
  private @Nullable PsiElement getChildAt(int index) {
    TreeElement child = getFirstChildNode();
    for (int i = 0; i < index; i++) {
      child = child.getTreeNext();
      if (child == null) {
        return null;
      }
    }
    return child == null ? null : child.getPsi();
  }
}
