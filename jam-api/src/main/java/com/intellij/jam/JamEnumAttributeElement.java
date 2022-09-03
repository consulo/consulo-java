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

import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import com.intellij.java.language.psi.PsiEnumConstant;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author peter
 */
public class JamEnumAttributeElement<T extends Enum<T>> extends JamAttributeElement<T> {
  private final Class<T> myModelEnum;

  public JamEnumAttributeElement(@Nonnull PsiElementRef<PsiAnnotation> parent, String attributeName, Class<T> modelEnum) {
    super(attributeName, parent);
    myModelEnum = modelEnum;
  }

  public JamEnumAttributeElement(PsiAnnotationMemberValue exactValue, Class<T> modelEnum) {
    super(exactValue);
    myModelEnum = modelEnum;
  }

  @Nullable
  public PsiEnumConstant getEnumConstant() {
    final PsiAnnotationMemberValue memberValue = getPsiElement();
    if (memberValue instanceof PsiReferenceExpression) {
      final PsiReferenceExpression psiReferenceExpression = (PsiReferenceExpression)memberValue;
      final PsiElement psiElement = psiReferenceExpression.resolve();
      if (psiElement instanceof PsiEnumConstant) {
        return (PsiEnumConstant)psiElement;
      }
    }
    return null;
  }

  public String getStringValue() {
    final PsiEnumConstant constant = getEnumConstant();
    if (constant != null) {
      return constant.getName();
    }
    return null;
  }

  public T getValue() {
    final String name = getStringValue();
    if (name == null) {
      return null;
    }

    try {
      return Enum.valueOf(myModelEnum, name);
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  public Class<T> getModelEnum() {
    return myModelEnum;
  }

}
