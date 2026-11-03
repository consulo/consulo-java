// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.javadoc.PsiMarkdownLink;
import consulo.language.impl.psi.CompositePsiElement;

public final class PsiMarkdownLinkImpl extends CompositePsiElement implements PsiMarkdownLink {
  public PsiMarkdownLinkImpl() {
    super(JavaDocElementType.DOC_MARKDOWN_LINK);
  }
}
