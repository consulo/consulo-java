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
                                          @jakarta.annotation.Nonnull PsiParameter[] parameters,
                                          @jakarta.annotation.Nonnull PsiExpression[] arguments,
                                          @jakarta.annotation.Nonnull PsiSubstitutor partialSubstitutor,
                                          @Nullable PsiElement parent,
                                          @jakarta.annotation.Nonnull ParameterTypeInferencePolicy policy);

  @Nonnull
  PsiSubstitutor inferTypeArguments(@jakarta.annotation.Nonnull PsiTypeParameter[] typeParameters,
                                    @Nonnull PsiParameter[] parameters,
                                    @jakarta.annotation.Nonnull PsiExpression[] arguments,
                                    @Nonnull PsiSubstitutor partialSubstitutor,
                                    @jakarta.annotation.Nonnull PsiElement parent,
                                    @jakarta.annotation.Nonnull ParameterTypeInferencePolicy policy,
                                    @jakarta.annotation.Nonnull LanguageLevel languageLevel);

  @jakarta.annotation.Nonnull
  PsiSubstitutor inferTypeArguments(@jakarta.annotation.Nonnull PsiTypeParameter[] typeParameters, @jakarta.annotation.Nonnull PsiType[] leftTypes, @jakarta.annotation.Nonnull PsiType[] rightTypes, @Nonnull LanguageLevel languageLevel);

  PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam, PsiType param, PsiType arg, boolean isContraVariantPosition, LanguageLevel languageLevel);
}
