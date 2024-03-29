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
 * A type which represents a function denoted by a lambda expression.
 */
public class PsiLambdaExpressionType extends PsiType {
  private final PsiLambdaExpression myExpression;

  public PsiLambdaExpressionType(@Nonnull PsiLambdaExpression expression) {
    super(TypeAnnotationProvider.EMPTY);
    myExpression = expression;
  }

  @Nonnull
  @Override
  public String getPresentableText() {
    return getCanonicalText();
  }

  @Nonnull
  @Override
  public String getCanonicalText() {
    return "<lambda expression>";
  }

  @Override
  public boolean isValid() {
    return myExpression.isValid();
  }

  @Override
  public boolean equalsToText(@Nonnull String text) {
    return false;
  }

  @Override
  public <A> A accept(@Nonnull PsiTypeVisitor<A> visitor) {
    return visitor.visitLambdaExpressionType(this);
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

  public PsiLambdaExpression getExpression() {
    return myExpression;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof PsiLambdaExpressionType && myExpression.equals(((PsiLambdaExpressionType) obj).myExpression);
  }

  @Override
  public int hashCode() {
    return myExpression.hashCode();
  }
}