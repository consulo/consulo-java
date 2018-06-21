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

import com.intellij.jam.JamTypeAttributeElement;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElementRef;
import com.intellij.psi.PsiType;
import com.intellij.util.NullableFunction;
import javax.annotation.Nonnull;

import java.util.List;

/**
 * @author peter
 */
public abstract class JamTypeAttributeMeta<JamType> extends JamAttributeMeta<JamType> {

  public JamTypeAttributeMeta(String attrName) {
    super(attrName);
  }

  public static class Collection extends JamTypeAttributeMeta<List<JamTypeAttributeElement>> {
    public Collection(String attrName) {
      super(attrName);
    }

    @Nonnull
    public List<JamTypeAttributeElement> getJam(PsiElementRef<PsiAnnotation> anno) {
      return getCollectionJam(anno, new NullableFunction<PsiAnnotationMemberValue, JamTypeAttributeElement>() {
        public JamTypeAttributeElement fun(PsiAnnotationMemberValue psiAnnotationMemberValue) {
          return new JamTypeAttributeElement(psiAnnotationMemberValue);
        }
      });
    }
  }

  public static class Single extends JamTypeAttributeMeta<JamTypeAttributeElement> {

    public Single(String attrName) {
      super(attrName);
    }

    @Nonnull
    public JamTypeAttributeElement getJam(PsiElementRef<PsiAnnotation> anno) {
      return new JamTypeAttributeElement(anno, getAttributeLink().getAttributeName());
    }

    @Nonnull
    public JamTypeAttributeElement getJam(PsiElementRef<PsiAnnotation> anno, final Factory<PsiType> defaultValue) {
      return new JamTypeAttributeElement(anno, getAttributeLink().getAttributeName()) {
        @Override
        public PsiType getValue() {
          final PsiType psiType = super.getValue();
          return psiType == null ? defaultValue.create() : psiType;
        }
      };
    }
  }

}
