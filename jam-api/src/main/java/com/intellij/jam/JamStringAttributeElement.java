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

import com.intellij.jam.model.util.JamCommonUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.ref.AnnotationAttributeChildLink;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementRef;
import consulo.xml.util.xml.MutableGenericValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author peter
 */
public class JamStringAttributeElement<T> extends JamAttributeElement<T> implements MutableGenericValue<T> {
  private final JamConverter<T> myConverter;

  public JamStringAttributeElement(@Nonnull PsiElementRef<PsiAnnotation> parent, String attributeName, JamConverter<T> converter) {
    super(attributeName, parent);
    myConverter = converter;
  }

  public JamStringAttributeElement(@Nonnull PsiAnnotationMemberValue exactValue, JamConverter<T> converter) {
    super(exactValue);
    myConverter = converter;
  }

  public String getStringValue() {
    final PsiAnnotationMemberValue value = getPsiElement();
    if (value == null) {
      return null;
    }
    return JamCommonUtil.getObjectValue(value, String.class);
  }

  @Nullable
  public PsiLiteral getPsiLiteral() {
    final PsiAnnotationMemberValue value = getPsiElement();
    return value instanceof PsiLiteral ? (PsiLiteral) value : null;
  }

  public T getValue() {
    return myConverter.fromString(getStringValue(), this);
  }

  public JamConverter<T> getConverter() {
    return myConverter;
  }

  public void setStringValue(@Nullable String value) {
    final PsiAnnotationMemberValue existing = getPsiElement();
    if (value == null && existing == null) {
      return;
    }

    final PsiAnnotation annotation = getParentAnnotationElement().ensurePsiElementExists();
    PsiAnnotationSupport support = PsiAnnotationSupport.forLanguage(annotation.getLanguage());
    assert support != null;

    final PsiAnnotationMemberValue valueElement = value == null
                                       ? null
                                       : support.createLiteralValue(value, annotation);

    final AnnotationAttributeChildLink attributeLink = getAttributeLink();
    if (attributeLink != null) {
      annotation.setDeclaredAttributeValue(attributeLink.getAttributeName(), valueElement);
    } else {
      if (valueElement != null) {
        existing.replace(valueElement);
      } else {
        final PsiElement parent = existing.getParent();
        (parent instanceof PsiNameValuePair ? parent : existing).delete();
      }
    }
  }

  public void setValue(T value) {
    String s = myConverter.toString(value, this);
    setStringValue(s);
  }

}
