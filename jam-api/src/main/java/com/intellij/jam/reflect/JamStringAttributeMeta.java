/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.intellij.jam.reflect;

import com.intellij.jam.JamConverter;
import com.intellij.jam.JamStringAttributeElement;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import consulo.language.psi.PsiElementRef;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author peter
 */
public abstract class JamStringAttributeMeta<T, JamType> extends JamAttributeMeta<JamType> {
  protected final JamConverter<T> myConverter;

  public JamStringAttributeMeta(String attrName, JamConverter<T> converter) {
    super(attrName);
    myConverter = converter;
  }

  public JamConverter<T> getConverter() {
    return myConverter;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    JamStringAttributeMeta that = (JamStringAttributeMeta)o;

    if (!myConverter.equals(that.myConverter)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myConverter.hashCode();
    return result;
  }

  public static class Collection<T> extends JamStringAttributeMeta<T, List<JamStringAttributeElement<T>>> {
    public Collection(String attrName, JamConverter<T> converter) {
      super(attrName, converter);
    }

    @Nonnull
    public List<JamStringAttributeElement<T>> getJam(PsiElementRef<PsiAnnotation> anno) {
      return getCollectionJam(anno, new Function<PsiAnnotationMemberValue, JamStringAttributeElement<T>>() {
        public JamStringAttributeElement<T> apply(PsiAnnotationMemberValue psiAnnotationMemberValue) {
          return new JamStringAttributeElement<T>(psiAnnotationMemberValue, myConverter);
        }
      });
    }

    public JamStringAttributeElement<T> addAttribute(PsiElementRef<PsiAnnotation> annoRef, String stringValue) {
      return new JamStringAttributeElement<T>(addAttribute(annoRef, "\"" + stringValue + "\"", getAttributeLink()), myConverter);
    }

  }

  public static class Single<T> extends JamStringAttributeMeta<T, JamStringAttributeElement<T>> {
    public Single(String attrName, JamConverter<T> converter) {
      super(attrName, converter);
    }

    @jakarta.annotation.Nonnull
    public JamStringAttributeElement<T> getJam(PsiElementRef<PsiAnnotation> anno, @Nonnull final Supplier<T> defaultValue) {
      return new JamStringAttributeElement<T>(anno, getAttributeLink().getAttributeName(), myConverter) {
        @Override
        public T getValue() {
          final T value = super.getValue();
          return value == null ? defaultValue.get() : value;
        }
      };
    }

    @Nonnull
    public JamStringAttributeElement<T> getJam(PsiElementRef<PsiAnnotation> anno) {
      return new JamStringAttributeElement<T>(anno, getAttributeLink().getAttributeName(), myConverter);
    }
  }

}
