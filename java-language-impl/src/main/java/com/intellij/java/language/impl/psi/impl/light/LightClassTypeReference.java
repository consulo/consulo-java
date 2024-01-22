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
package com.intellij.java.language.impl.psi.impl.light;

import com.intellij.java.language.psi.JavaResolveResult;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class LightClassTypeReference extends LightClassReferenceBase implements PsiJavaCodeReferenceElement {

  @Nonnull
  private final
  PsiClassType myType;

  private LightClassTypeReference(@Nonnull PsiManager manager, @Nonnull String text, @jakarta.annotation.Nonnull PsiClassType type) {
    super(manager, text);
    myType = type;
  }

  public LightClassTypeReference(@Nonnull PsiManager manager, @jakarta.annotation.Nonnull PsiClassType type) {
    this(manager, type.getCanonicalText(true), type);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return myType.resolve();
  }

  @jakarta.annotation.Nonnull
  @Override
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    return myType.resolveGenerics();
  }

  @jakarta.annotation.Nullable
  @Override
  public String getReferenceName() {
    return myType.getClassName();
  }

  @Override
  public PsiElement copy() {
    return new LightClassTypeReference(myManager, myText, myType);
  }

  @Override
  public boolean isValid() {
    return myType.isValid();
  }

  @jakarta.annotation.Nonnull
  public PsiClassType getType() {
    return myType;
  }

  @jakarta.annotation.Nonnull
  @Override
  public GlobalSearchScope getResolveScope() {
    return myType.getResolveScope();
  }
}
