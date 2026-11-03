// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaDocElementType;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.javadoc.PsiDocReferenceHolder;
import consulo.language.impl.psi.LazyParseablePsiElement;
import consulo.language.psi.PsiDelegateReference;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import org.jspecify.annotations.Nullable;

public final class PsiDocReferenceHolderImpl extends LazyParseablePsiElement implements PsiDocReferenceHolder {
  public PsiDocReferenceHolderImpl(CharSequence text) {
    super(JavaDocElementType.DOC_REFERENCE_HOLDER, text);
  }

  @Override
  public void accept(PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocReferenceHolder(this);
    }
    super.accept(visitor);
  }

  @Override
  public PsiReference getReference() {
    return new PsiDelegateReference(PsiDocMethodOrFieldRef.getReference(this)) {
      @Override
      public @Nullable PsiElement resolve() {
        PsiReference childRef = PsiDocReferenceHolderImpl.this.getFirstChild().getReference();
        if (childRef != null && childRef.resolve() != null) {
          return null;
        }
        return super.resolve();
      }
    };
  }
}
