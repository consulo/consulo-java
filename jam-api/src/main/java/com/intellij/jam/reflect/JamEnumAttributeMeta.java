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
package com.intellij.jam.reflect;

import com.intellij.jam.JamEnumAttributeElement;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import consulo.language.psi.PsiElementRef;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;

/**
 * @author peter
 */
public abstract class JamEnumAttributeMeta<T extends Enum<T>, JamType> extends JamAttributeMeta<JamType> {
  protected final Class<T> myModelEnum;

  protected JamEnumAttributeMeta(String attrName, Class<T> modelEnum) {
    super(attrName);
    myModelEnum = modelEnum;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    JamEnumAttributeMeta that = (JamEnumAttributeMeta)o;

    if (!myModelEnum.equals(that.myModelEnum)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myModelEnum.hashCode();
    return result;
  }

  public static class Collection<T extends Enum<T>> extends JamEnumAttributeMeta<T, List<JamEnumAttributeElement<T>>> {
    public Collection(String attrName, Class<T> modelEnum) {
      super(attrName, modelEnum);
    }

    @Nonnull
    public List<JamEnumAttributeElement<T>> getJam(PsiElementRef<PsiAnnotation> anno) {
      return getCollectionJam(anno, new Function<PsiAnnotationMemberValue, JamEnumAttributeElement<T>>() {
        public JamEnumAttributeElement<T> apply(PsiAnnotationMemberValue psiAnnotationMemberValue) {
          return new JamEnumAttributeElement<T>(psiAnnotationMemberValue, myModelEnum);
        }
      });
    }
  }

  public static class Single<T extends Enum<T>> extends JamEnumAttributeMeta<T, JamEnumAttributeElement<T>> {
    public Single(String attrName, Class<T> modelEnum) {
      super(attrName, modelEnum);
    }

    @Nonnull
    public JamEnumAttributeElement<T> getJam(PsiElementRef<PsiAnnotation> anno) {
      return new JamEnumAttributeElement<T>(anno, getAttributeLink().getAttributeName(), myModelEnum);
    }

    @Nonnull
    public JamEnumAttributeElement<T> getJam(PsiElementRef<PsiAnnotation> anno, final T defaultValue) {
      return new JamEnumAttributeElement<T>(anno, getAttributeLink().getAttributeName(), myModelEnum) {
        @Override
        public T getValue() {
          final T value = super.getValue();
          return value == null ? defaultValue : value;
        }
      };
    }
  }
}
