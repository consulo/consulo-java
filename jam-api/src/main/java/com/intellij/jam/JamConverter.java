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
package com.intellij.jam;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.psi.PsiReference;

/**
 * @author peter
 */
public abstract class JamConverter<T> {

  public static final JamConverter<String> DUMMY_CONVERTER = new JamConverter<String>() {
    @Override
    public String fromString(@Nullable String s, JamStringAttributeElement<String> context) {
      return s;
    }

    @Override
    public String toString(@Nullable String s, JamElement context) {
      return s;
    }
  };

  @Nullable
  public abstract T fromString(@Nullable String s, JamStringAttributeElement<T> context);

  @Nullable
  public String toString(@Nullable T s, JamElement context) {
    throw new UnsupportedOperationException("toString() not supported for " + getClass());
  }

  @Nonnull
  public PsiReference[] createReferences(JamStringAttributeElement<T> context) {
    return PsiReference.EMPTY_ARRAY;
  }

}
