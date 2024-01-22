/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.java.language.psi.util;

import jakarta.annotation.Nonnull;

import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiTypeParameter;

/**
 * @author cdr
 */
public interface MethodSignature {
  MethodSignature[] EMPTY_ARRAY = new MethodSignature[0];

  @jakarta.annotation.Nonnull
  PsiSubstitutor getSubstitutor();

  @jakarta.annotation.Nonnull
  String getName();

  /**
   * @return array of parameter types (already substituted)
   */
  @Nonnull
  PsiType[] getParameterTypes();

  @jakarta.annotation.Nonnull
  PsiTypeParameter[] getTypeParameters();

  boolean isRaw();

  boolean isConstructor();
}
