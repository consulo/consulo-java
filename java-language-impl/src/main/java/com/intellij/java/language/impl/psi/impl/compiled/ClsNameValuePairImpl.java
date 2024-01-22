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
package com.intellij.java.language.impl.psi.impl.compiled;

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiAnnotationMemberValue;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiNameValuePair;
import consulo.language.impl.ast.TreeElement;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ven
 */
class ClsNameValuePairImpl extends ClsElementImpl implements PsiNameValuePair {
  private final ClsElementImpl myParent;
  private final ClsIdentifierImpl myNameIdentifier;
  private final PsiAnnotationMemberValue myMemberValue;

  ClsNameValuePairImpl(@jakarta.annotation.Nonnull ClsElementImpl parent, @Nullable String name, @Nonnull PsiAnnotationMemberValue value) {
    myParent = parent;
    myNameIdentifier = name != null ? new ClsIdentifierImpl(this, name) : null;
    myMemberValue = ClsParsingUtil.getMemberValue(value, this);
  }

  @Override
  public void appendMirrorText(int indentLevel, @jakarta.annotation.Nonnull StringBuilder buffer) {
    appendText(myNameIdentifier, 0, buffer, " = ");
    appendText(myMemberValue, 0, buffer);
  }

  @Override
  public void setMirror(@jakarta.annotation.Nonnull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);

    PsiNameValuePair mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    setMirrorIfPresent(getNameIdentifier(), mirror.getNameIdentifier());
    setMirrorIfPresent(getValue(), mirror.getValue());
  }

  @Override
  @Nonnull
  public PsiElement[] getChildren() {
    if (myNameIdentifier != null) {
      return new PsiElement[]{
          myNameIdentifier,
          myMemberValue
      };
    } else {
      return new PsiElement[]{myMemberValue};
    }
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitNameValuePair(this);
    } else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  @Override
  public String getName() {
    return myNameIdentifier != null ? myNameIdentifier.getText() : null;
  }

  @Override
  public String getLiteralValue() {
    return null;
  }

  @Override
  public PsiAnnotationMemberValue getValue() {
    return myMemberValue;
  }

  @Override
  @Nonnull
  public PsiAnnotationMemberValue setValue(@Nonnull PsiAnnotationMemberValue newValue) {
    throw cannotModifyException(this);
  }
}
