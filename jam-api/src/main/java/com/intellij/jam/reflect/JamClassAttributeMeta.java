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

import com.intellij.jam.JamClassAttributeElement;
import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiElementRef;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author peter
 */
public abstract class JamClassAttributeMeta<JamType> extends JamAttributeMeta<JamType> {

  public JamClassAttributeMeta(String attrName) {
    super(attrName);
  }

  public static class Collection extends JamClassAttributeMeta<List<JamClassAttributeElement>> {
    public Collection(String attrName) {
      super(attrName);
    }

    @jakarta.annotation.Nonnull
    public List<JamClassAttributeElement> getJam(PsiElementRef<PsiAnnotation> anno) {
      return getCollectionJam(anno, new Function<PsiAnnotationMemberValue, JamClassAttributeElement>() {
        public JamClassAttributeElement apply(PsiAnnotationMemberValue psiAnnotationMemberValue) {
          return new JamClassAttributeElement(psiAnnotationMemberValue);
        }
      });
    }
  }

  public static class Single extends JamClassAttributeMeta<JamClassAttributeElement> {

    public Single(String attrName) {
      super(attrName);
    }

    @Nonnull
    public JamClassAttributeElement getJam(PsiElementRef<PsiAnnotation> anno) {
      return new JamClassAttributeElement(anno, getAttributeLink().getAttributeName());
    }

    @Nonnull
    public JamClassAttributeElement getJam(PsiElementRef<PsiAnnotation> anno, final Supplier<PsiClass> defaultValue) {
      return new JamClassAttributeElement(anno, getAttributeLink().getAttributeName()) {
        @Override
        public PsiClass getValue() {
          final PsiClass psiClass = super.getValue();
          return psiClass == null ? defaultValue.get() : psiClass;
        }
      };
    }
  }

}
