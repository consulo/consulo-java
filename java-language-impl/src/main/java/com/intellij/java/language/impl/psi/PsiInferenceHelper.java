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
package com.intellij.java.language.impl.psi;

import com.intellij.java.language.LanguageLevel;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * User: anna
 */
public interface PsiInferenceHelper {
  /**
   * @return {@link PsiType#NULL} iff no type could be inferred
   * null         iff the type inferred is raw
   * inferred type otherwise
   */
  PsiType inferTypeForMethodTypeParameter(@Nonnull PsiTypeParameter typeParameter,
                                          @Nonnull PsiParameter[] parameters,
                                          @Nonnull PsiExpression[] arguments,
                                          @Nonnull PsiSubstitutor partialSubstitutor,
                                          @Nullable PsiElement parent,
                                          @Nonnull ParameterTypeInferencePolicy policy);

  @Nonnull
  PsiSubstitutor inferTypeArguments(@Nonnull PsiTypeParameter[] typeParameters,
                                    @Nonnull PsiParameter[] parameters,
                                    @Nonnull PsiExpression[] arguments,
                                    @Nonnull PsiSubstitutor partialSubstitutor,
                                    @Nonnull PsiElement parent,
                                    @Nonnull ParameterTypeInferencePolicy policy,
                                    @Nonnull LanguageLevel languageLevel);

  @Nonnull
  PsiSubstitutor inferTypeArguments(@Nonnull PsiTypeParameter[] typeParameters, @Nonnull PsiType[] leftTypes, @Nonnull PsiType[] rightTypes, @Nonnull LanguageLevel languageLevel);

  PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam, PsiType param, PsiType arg, boolean isContraVariantPosition, LanguageLevel languageLevel);
}
