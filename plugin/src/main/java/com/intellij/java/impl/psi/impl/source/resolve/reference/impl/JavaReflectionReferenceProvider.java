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
package com.intellij.java.impl.psi.impl.source.resolve.reference.impl;

import com.intellij.java.language.psi.PsiExpressionList;
import com.intellij.java.language.psi.PsiLiteralExpression;
import com.intellij.java.language.psi.PsiMethodCallExpression;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.util.ProcessingContext;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Konstantin Bulenkov
 */
abstract class JavaReflectionReferenceProvider extends PsiReferenceProvider {
  @Nonnull
  @Override
  public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context) {
    if (element instanceof PsiLiteralExpression) {
      PsiLiteralExpression literal = (PsiLiteralExpression) element;
      if (literal.getValue() instanceof String) {
        PsiElement parent = element.getParent();
        if (parent instanceof PsiExpressionList) {
          PsiElement grandParent = parent.getParent();
          if (grandParent instanceof PsiMethodCallExpression) {
            PsiReferenceExpression methodReference = ((PsiMethodCallExpression) grandParent).getMethodExpression();
            PsiReference[] references = getReferencesByMethod(literal, methodReference, context);
            if (references != null) {
              return references;
            }
          }
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  @Nullable
  protected abstract PsiReference[] getReferencesByMethod(@Nonnull PsiLiteralExpression literalArgument, @jakarta.annotation.Nonnull PsiReferenceExpression methodReference, @Nonnull ProcessingContext context);
}
