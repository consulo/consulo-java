/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.java.language.psi;

import consulo.language.psi.scope.GlobalSearchScope;

import jakarta.annotation.Nonnull;

/**
 * A type which represents an omitted type for parameter of lambda expression.
 */
public class PsiLambdaParameterType extends PsiType {
  private final PsiParameter myParameter;

  public PsiLambdaParameterType(@Nonnull PsiParameter parameter) {
    super(TypeAnnotationProvider.EMPTY);
    myParameter = parameter;
  }

  @Nonnull
  @Override
  public String getPresentableText() {
    return getCanonicalText();
  }

  @Nonnull
  @Override
  public String getCanonicalText() {
    return "<lambda parameter>";
  }

  @Override
  public boolean isValid() {
    return myParameter.isValid();
  }

  @Override
  public boolean equalsToText(@Nonnull String text) {
    return false;
  }

  @Override
  public <A> A accept(@Nonnull PsiTypeVisitor<A> visitor) {
    return visitor.visitType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @Nonnull
  @Override
  public PsiType[] getSuperTypes() {
    return PsiType.EMPTY_ARRAY;
  }

  public PsiParameter getParameter() {
    return myParameter;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof PsiLambdaParameterType && myParameter.equals(((PsiLambdaParameterType) o).myParameter);
  }

  @Override
  public int hashCode() {
    return myParameter.hashCode();
  }
}