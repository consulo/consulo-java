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
 * Represents an array type.
 *
 * @author max
 */
public class PsiArrayType extends PsiType.Stub {
  private final PsiType myComponentType;

  public PsiArrayType(@Nonnull PsiType componentType) {
    this(componentType, TypeAnnotationProvider.EMPTY);
  }

  public PsiArrayType(@Nonnull PsiType componentType, @Nonnull PsiAnnotation[] annotations) {
    super(annotations);
    myComponentType = componentType;
  }

  public PsiArrayType(@Nonnull PsiType componentType, @Nonnull TypeAnnotationProvider provider) {
    super(provider);
    myComponentType = componentType;
  }

  @Nonnull
  @Override
  public String getPresentableText(boolean annotated) {
    return getText(myComponentType.getPresentableText(), "[]", false, annotated);
  }

  @Nonnull
  @Override
  public String getCanonicalText(boolean annotated) {
    return getText(myComponentType.getCanonicalText(annotated), "[]", true, annotated);
  }

  @Nonnull
  @Override
  public String getInternalCanonicalText() {
    return getText(myComponentType.getInternalCanonicalText(), "[]", true, true);
  }

  protected String getText(@Nonnull String prefix, @Nonnull String suffix, boolean qualified, boolean annotated) {
    StringBuilder sb = new StringBuilder(prefix.length() + suffix.length());
    sb.append(prefix);
    if (annotated) {
      PsiAnnotation[] annotations = getAnnotations();
      if (annotations.length != 0) {
        sb.append(' ');
        PsiNameHelper.appendAnnotations(sb, annotations, qualified);
      }
    }
    sb.append(suffix);
    return sb.toString();
  }

  @Override
  public boolean isValid() {
    return myComponentType.isValid();
  }

  @Override
  public boolean equalsToText(@Nonnull String text) {
    return text.endsWith("[]") && myComponentType.equalsToText(text.substring(0, text.length() - 2));
  }

  @Override
  public <A> A accept(@Nonnull PsiTypeVisitor<A> visitor) {
    return visitor.visitArrayType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myComponentType.getResolveScope();
  }

  @Override
  @Nonnull
  public PsiType[] getSuperTypes() {
    final PsiType[] superTypes = myComponentType.getSuperTypes();
    final PsiType[] result = createArray(superTypes.length);
    for (int i = 0; i < superTypes.length; i++) {
      result[i] = superTypes[i].createArrayType();
    }
    return result;
  }

  /**
   * Returns the component type of the array.
   *
   * @return the component type instance.
   */
  @Nonnull
  public PsiType getComponentType() {
    return myComponentType;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof PsiArrayType &&
        (this instanceof PsiEllipsisType == obj instanceof PsiEllipsisType) &&
        myComponentType.equals(((PsiArrayType) obj).getComponentType());
  }

  @Override
  public int hashCode() {
    return myComponentType.hashCode() * 3;
  }
}