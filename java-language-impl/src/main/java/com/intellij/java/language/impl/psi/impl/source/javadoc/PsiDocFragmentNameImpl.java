// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.language.impl.psi.impl.source.javadoc;

import com.intellij.java.language.impl.psi.impl.source.Constants;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.javadoc.PsiDocFragmentName;
import consulo.language.impl.psi.CompositePsiElement;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

// TODO JetBrains also overrides getOwnReferences() here, returning the fragment name symbol reference
//  (JavaPsiImplementationHelper.getFragmentNameSymbol) - needs the Symbol API, which Consulo does not have
public class PsiDocFragmentNameImpl extends CompositePsiElement implements PsiDocFragmentName, Constants {
  public PsiDocFragmentNameImpl() {
    super(DOC_FRAGMENT_NAME);
  }

  @Override
  public @Nullable PsiClass getScope() {
    final PsiElement parent = getParent();
    return parent instanceof PsiDocFragmentRefImpl ? ((PsiDocFragmentRefImpl)parent).getScope() : null;
  }
}
