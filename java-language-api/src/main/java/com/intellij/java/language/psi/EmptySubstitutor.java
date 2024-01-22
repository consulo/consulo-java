/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.java.language.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;

/**
 * The substitutor which does not provide any mappings for the type parameters.
 *
 * @see PsiSubstitutor#EMPTY
 */
public final class EmptySubstitutor implements PsiSubstitutor {
  public static EmptySubstitutor getInstance() {
    return Holder.INSTANCE;
  }

  @Override
  public PsiType substitute(@Nonnull PsiTypeParameter typeParameter) {
    return JavaPsiFacade.getElementFactory(typeParameter.getProject()).createType(typeParameter);
  }

  @Override
  public PsiType substitute(PsiType type) {
    return type;
  }

  @Override
  public PsiType substituteWithBoundsPromotion(@Nonnull PsiTypeParameter typeParameter) {
    return JavaPsiFacade.getElementFactory(typeParameter.getProject()).createType(typeParameter);
  }

  @Nonnull
  @Override
  public PsiSubstitutor put(@Nonnull PsiTypeParameter classParameter, PsiType mapping) {
    if (mapping != null) {
      PsiUtil.ensureValidType(mapping);
    }
    return PsiSubstitutorFactory.getInstance().createSubstitutor(classParameter, mapping);
  }

  @jakarta.annotation.Nonnull
  @Override
  public PsiSubstitutor putAll(@Nonnull PsiClass parentClass, PsiType[] mappings) {
    if (!parentClass.hasTypeParameters())
      return this;
    return PsiSubstitutorFactory.getInstance().createSubstitutor(parentClass, mappings);
  }

  @Nonnull
  @Override
  public PsiSubstitutor putAll(@jakarta.annotation.Nonnull PsiSubstitutor another) {
    return another;
  }

  @Nonnull
  @Override
  public PsiSubstitutor putAll(@Nonnull Map<? extends PsiTypeParameter, ? extends PsiType> map) {
    return map.isEmpty() ? EMPTY : PsiSubstitutorFactory.getInstance().createSubstitutor(map);
  }

  @Override
  @Nonnull
  public Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
    return Collections.emptyMap();
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void ensureValid() {
  }

  @Override
  public String toString() {
    return "EmptySubstitutor";
  }

  private static class Holder {
    private static final EmptySubstitutor INSTANCE = new EmptySubstitutor();
  }
}
