/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.resolve.PsiScopeProcessor;
import jakarta.annotation.Nonnull;

/**
 * Represents a reference found in Java code.
 */
public interface PsiJavaReference extends PsiPolyVariantReference {
  /**
   * Passes all variants to which the reference may resolve to the specified
   * processor.
   *
   * @param processor the processor accepting the variants.s
   */
  void processVariants(PsiScopeProcessor processor);

  /**
   * Resolves the reference and returns the result as a {@link JavaResolveResult}
   * instead of a plain {@link PsiElement}.
   *
   * @param incompleteCode if true, the code in the context of which the reference is
   * being resolved is considered incomplete, and the method may return an invalid
   * result.
   * @return the result of the resolve.
   */
  @Nonnull
  JavaResolveResult advancedResolve(boolean incompleteCode);
  
  @Override
  @Nonnull
  JavaResolveResult[] multiResolve(boolean incompleteCode);
}
