// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.language.impl.psi.impl.source;

import com.intellij.java.language.impl.psi.impl.source.tree.JavaElementType;
import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiJavaModule;
import com.intellij.java.language.psi.PsiJavaModuleReference;
import com.intellij.java.language.psi.PsiJavaModuleReferenceElement;
import consulo.application.util.CachedValueProvider;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.LanguageCachedValueUtil;

import javax.annotation.Nonnull;

public class PsiJavaModuleReferenceElementImpl extends CompositePsiElement implements PsiJavaModuleReferenceElement {
  public PsiJavaModuleReferenceElementImpl() {
    super(JavaElementType.MODULE_REFERENCE);
  }

  @Nonnull
  @Override
  public String getReferenceText() {
    StringBuilder sb = new StringBuilder();
    for (PsiElement e = getFirstChild(); e != null; e = e.getNextSibling()) {
      if (!(e instanceof PsiWhiteSpace) && !(e instanceof PsiComment)) {
        sb.append(e.getText());
      }
    }
    return sb.toString();
  }

  @Override
  public PsiJavaModuleReference getReference() {
    if (getParent() instanceof PsiJavaModule && !(getContainingFile() instanceof JavaDummyHolder)) {
      return null;  // module name identifier is not a reference
    } else {
      return LanguageCachedValueUtil.getCachedValue(this, () -> CachedValueProvider.Result.create(new PsiJavaModuleReferenceImpl(this), this));
    }
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitModuleReferenceElement(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiJavaModuleReference";
  }
}