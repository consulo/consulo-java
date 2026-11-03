// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import consulo.language.ast.IElementType;
import consulo.language.impl.psi.LeafPsiElement;

public final class PsiDocFormatString extends LeafPsiElement {
  public PsiDocFormatString(IElementType type, CharSequence text) {
    super(type, text);
  }
}
