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
import com.intellij.java.language.psi.ref.AnnotationAttributeChildLink;
import consulo.language.psi.PsiElementRef;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.xml.util.xml.GenericValue;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author peter
 */
public abstract class JamAttributeElement<T> implements JamElement, GenericValue<T> {
  private final PsiElementRef<PsiAnnotation> myParent;
  @Nullable
  private final AnnotationAttributeChildLink myAttributeLink;
  @Nullable
  private final PsiAnnotationMemberValue myExactValue;

  public JamAttributeElement(String attributeName, @Nonnull PsiElementRef<PsiAnnotation> parent) {
    myAttributeLink = new AnnotationAttributeChildLink(attributeName);
    myExactValue = null;
    myParent = parent;
  }

  protected JamAttributeElement(PsiAnnotationMemberValue exactValue) {
    myExactValue = exactValue;
    myAttributeLink = null;
    myParent = PsiElementRef.real(PsiTreeUtil.getParentOfType(exactValue, PsiAnnotation.class));
  }

  @Nullable
  protected AnnotationAttributeChildLink getAttributeLink() {
    return myAttributeLink;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JamAttributeElement that = (JamAttributeElement)o;

    if (myAttributeLink != null ? !myAttributeLink.equals(that.myAttributeLink) : that.myAttributeLink != null) return false;
    if (myExactValue != null ? !myExactValue.equals(that.myExactValue) : that.myExactValue != null) return false;
    if (!myParent.equals(that.myParent)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myParent.hashCode();
    result = 31 * result + (myAttributeLink != null ? myAttributeLink.hashCode() : 0);
    result = 31 * result + (myExactValue != null ? myExactValue.hashCode() : 0);
    return result;
  }

  public PsiManager getPsiManager() {
    return myParent.getPsiManager();
  }

  public boolean isValid() {
    return myParent.isValid();
  }

  @Nullable
  public PsiAnnotationMemberValue getPsiElement() {
    if (myExactValue == null) {
      assert myAttributeLink != null;
      return myAttributeLink.findLinkedChild(myParent.getPsiElement());
    }
    return myExactValue;
  }

  public PsiElementRef<PsiAnnotation> getParentAnnotationElement() {
    return myParent;
  }
}
