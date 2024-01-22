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

import com.intellij.java.language.psi.*;
import jakarta.annotation.Nonnull;

import jakarta.annotation.Nullable;

public class MethodSignatureHandMade extends MethodSignatureBase {
  private final String myName;
  private final boolean myIsConstructor;

  MethodSignatureHandMade(@Nonnull String name,
                          @Nullable PsiParameterList parameterList,
                          @Nullable PsiTypeParameterList typeParameterList,
                          @jakarta.annotation.Nonnull PsiSubstitutor substitutor,
                          boolean isConstructor) {
    super(substitutor, parameterList, typeParameterList);
    myName = name;
    myIsConstructor = isConstructor;
  }

  MethodSignatureHandMade(@jakarta.annotation.Nonnull String name,
                          @jakarta.annotation.Nonnull PsiType[] parameterTypes,
                          @jakarta.annotation.Nonnull PsiTypeParameter[] typeParameters,
                          @jakarta.annotation.Nonnull PsiSubstitutor substitutor,
                          boolean isConstructor) {
    super(substitutor, parameterTypes, typeParameters);
    myName = name;
    myIsConstructor = isConstructor;
  }


  @jakarta.annotation.Nonnull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isRaw() {
    for (final PsiTypeParameter typeParameter : myTypeParameters) {
      if (getSubstitutor().substitute(typeParameter) == null) return true;
    }
    return false;
  }

  @Override
  public boolean isConstructor() {
    return myIsConstructor;
  }
}
