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
package com.intellij.java.language.psi.ref;

import com.intellij.java.language.psi.*;
import consulo.language.psi.PsiChildLink;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;

/**
 * @author peter
*/
public class AnnotationAttributeChildLink extends PsiChildLink<PsiAnnotation, PsiAnnotationMemberValue> {
  private final String myAttributeName;

  public AnnotationAttributeChildLink(@jakarta.annotation.Nonnull @NonNls String attributeName) {
    myAttributeName = attributeName;
  }

  @jakarta.annotation.Nonnull
  public String getAttributeName() {
    return myAttributeName;
  }

  @Override
  public PsiAnnotationMemberValue findLinkedChild(@Nullable PsiAnnotation psiAnnotation) {
    if (psiAnnotation == null) return null;

    psiAnnotation.getText();
    return psiAnnotation.findDeclaredAttributeValue(myAttributeName);
  }

  @Override
  @Nonnull
  public PsiAnnotationMemberValue createChild(@jakarta.annotation.Nonnull PsiAnnotation psiAnnotation) throws IncorrectOperationException {
    psiAnnotation.getText();
    final PsiExpression nullValue = JavaPsiFacade.getElementFactory(psiAnnotation.getProject()).createExpressionFromText(PsiKeyword.NULL, null);
    psiAnnotation.setDeclaredAttributeValue(myAttributeName, nullValue);
    return ObjectUtil.assertNotNull(psiAnnotation.findDeclaredAttributeValue(myAttributeName));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final AnnotationAttributeChildLink link = (AnnotationAttributeChildLink)o;

    if (!myAttributeName.equals(link.myAttributeName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myAttributeName.hashCode();
  }
}
