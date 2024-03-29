/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.language.impl.psi;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeVisitor;
import consulo.language.psi.scope.GlobalSearchScope;

/**
 * Used in Generify refactoring
 */
public class Bottom extends PsiType {
  public static final Bottom BOTTOM = new Bottom();

  private Bottom() {
    super(PsiAnnotation.EMPTY_ARRAY);
  }

  @Override
  public String getPresentableText() {
    return "_";
  }

  @Override
  public String getCanonicalText() {
    return "_";
  }

  @Override
  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean equalsToText(String text) {
    return text.equals("_");
  }

  public boolean equals(Object o) {
    if (o instanceof Bottom) {
      return true;
    }

    return false;
  }

  @Override
  public <A> A accept(@Nonnull PsiTypeVisitor<A> visitor) {
    if (visitor instanceof PsiTypeVisitorEx) {
      return ((PsiTypeVisitorEx<A>)visitor).visitBottom(this);
    }
    else {
      return visitor.visitType(this);
    }
  }

  @Override
  @Nonnull
  public PsiType[] getSuperTypes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }
}
